"use strict";

import * as config from "./config.json";
import * as analyzers from "./resources/settings.json";
import * as mapping from "./resources/crossref_mapping.json";
import * as fs from "fs";
import client from "./my_connection";
import { Client } from "@elastic/elasticsearch";
import { createDecompressor } from "lzma-native";
import { split, map } from "event-stream";
import { BiblObj, ElasticIndexHeader, Options, RawCrossrefData } from "./index";
import { filterType, massage, round, sleep } from "./helpers";

const red = "\x1b[31m";
const reset = "\x1b[0m";

const getHealth = async (client: Client): Promise<void> => {
  const health = await client.cluster.health();
  console.log("ES Health --", health);
};

const indexData = async (options: Options, client: Client) => {
  const { body: indexExists } = await client.indices
    .exists({
      index: options.indexName,
    })
    .catch((err) => {
      console.error("Index exist check failed");
      throw err;
    });

  if (indexExists) {
    // remove previous index if it exists
    await client.indices.delete({ index: options.indexName }).catch((err) => {
      console.error("Index deletion failed");
      throw err;
    });
    console.log(`Index ${options.indexName} have been deleted`);
  }

  await client.indices
    .create({ index: options.indexName, body: analyzers })
    .catch((err) => {
      console.error("Index deletion failed");
      throw err;
    });
  console.log(`Index ${options.indexName} created`);

  await client.indices
    .putMapping({
      index: options.indexName,
      type: options.docType,
      body: mapping,
    })
    .catch((err) => {
      console.error("Mapping failed");
      throw err;
    });
  console.log(`Mapping created for index ${options.indexName}`);

  await index(options, client);
};

async function processAction(options: Options) {
  if (options.action === "health") {
    getHealth(client);
  } else if (options.action === "index") {
    indexData(options, client);
  }
}

const buildBibliographicField = (obj: Partial<BiblObj>): string => {
  return [
    obj.first_author,
    obj.title,
    obj.journal,
    obj.abbreviated_journal,
    obj.volume,
    obj.issue,
    obj.first_page,
    obj.year,
  ]
    .filter(Boolean)
    .join(" ");
};

/**
 * This function removes some non-used stuff from a crossref work entry,
 * in particular the citation information, which represent a considerable
 * amount of data.
 */
const createBiblObj = (data: RawCrossrefData): BiblObj => {
  // Just keep the fields we want to index
  // - Main fields (in the mapping)
  const obj: Partial<BiblObj> = {
    title: data.title,
    DOI: data.DOI,
    journal: data["container-title"],
    abbreviated_journal: data["short-container-title"],
    volume: data.volume,
    type: data.type,
    year: getYear(data),
  };

  if (data.author?.length > 0) {
    const { authors, firstAuthorName } = data.author.reduce(
      (acc, author) => {
        if (author.sequence === "first") {
          acc.firstAuthorName = author.family;
        }
        acc.authors.push(author.family);
        return acc;
      },
      { authors: [] as string[], firstAuthorName: "" }
    );

    obj.first_author = firstAuthorName;
    obj.author = authors.join(" ");
  }

  // parse page metadata to get the first page only
  if (data.page) {
    const firstPage = data.page.split(/,|-| /g)[0];
    if (firstPage) {
      obj.first_page = firstPage;
    }
  }

  if (data.issue) {
    obj.issue = data.issue;
  }

  // bibliograohic field is the concatenation of usual bibliographic metadata
  const biblio = buildBibliographicField(obj);
  if (biblio.length > 0) {
    obj.bibliographic = biblio;
  }

  return obj as BiblObj;
};

/**
 * Extracts the year from the different alternatives.
 * Goes in the order "issued", "published-online", "published-print" and lastly "created"
 * @param data The data to extract the year from
 */
const getYear = (data: RawCrossrefData): number => {
  // year is a date part (first one) in issued or created or published-online (we follow this order)
  if (data.issued?.["date-parts"]) {
    return data.issued["date-parts"][0][0];
  } else if (data["published-online"]?.["date-parts"]) {
    return data["published-online"]["date-parts"][0][0];
  } else if (data["published-print"]?.["date-parts"]) {
    return data["published-print"]["date-parts"][0][0];
  } else if (data.created?.["date-parts"]) {
    // this is deposit date, normally we will never use it, but it will ensure
    // that we always have a date as conservative fallback
    data.created["date-parts"][0][0];
  } else {
    return null;
  }
};

const index = async (options: Options, client: Client) => {
  const readStream = fs
    .createReadStream(options.dump)
    .pipe(createDecompressor())
    .pipe(split())
    .pipe(
      map((rawData: any, cb: any) => {
        cb(null, createBiblObj(massage(rawData)));
      })
    )
    .on("error", (error) => console.error("Error occurred: " + error))
    .on("finish", () => {
      console.log("Finished. ");
      const execTime = Date.now() - options.start.getTime();
      console.info("Execution time: %dms", execTime);
    });

  let i = 0;
  let indexed = 0;
  let batch: (ElasticIndexHeader | BiblObj)[] = [];

  readStream.on("data", async (doc: BiblObj) => {
    // filter some type of DOI not corresponding to a publication (e.g. component
    // of a publication)
    if (filterType(doc)) {
      return;
    }

    delete doc._id;
    delete doc.type;

    batch.push({
      index: {
        _index: options.indexName,
        _type: options.docType,
      },
    });

    batch.push(doc);
    i++;

    if (i % options.batchSize === 0) {
      const previousStart = new Date();
      const endTime = await sendBulk(batch, "false", client);

      let total_time = (endTime - options.start.getTime()) / 1000;
      let speed = round(
        options.batchSize / ((endTime - previousStart.getTime()) / 1000)
      );

      indexed += options.batchSize;
      console.log(
        `Loaded ${indexed} records in ${total_time} s (${speed} record/s)`
      );

      batch = [];
      i = 0;
    }
  });

  // When the stream ends write the remaining records
  readStream.on("end", async () => {
    if (batch.length > 0) {
      console.log("Loaded %s records", batch.length);
      await sendBulk(batch, "true", client);
      console.log("Completed crossref indexing.");
    }
    batch = [];
  });
};

const sendBulk = async (
  body: any,
  refresh: "true" | "false",
  client: Client,
  doOver = false
): Promise<number> => {
  try {
    const { body: bulkResponse } = await client.bulk({ refresh, body });

    if (bulkResponse.errors) {
      if (doOver) {
        throw bulkResponse.errors;
      }

      console.log(
        "Bulk is rejected... let's medidate 10 seconds about the illusion of time and consciousness"
      );
      // let's just wait and re-send the bulk request with increased
      // timeout to be on the safe side
      console.log("Waiting for 10 seconds");
      await sleep(20000); // -> this is blocking... time for elasticsearch to do whatever it does
      // and be in a better mood to accept this bulk
      return sendBulk(body, refresh, client, true);
    }

    return Date.now();
  } catch (err) {
    console.error(err);
  }
};

/**
 * Init the main object with paths passed with the command line
 */
async function init(): Promise<Options> {
  const options: Options = {
    action: "health",
    concurrency: 100,
    start: new Date(),
    ...config,
  };

  for (let i = 2, len = process.argv.length; i < len; i++) {
    if (process.argv[i - 1] === "-dump") {
      options.dump = process.argv[i];
    } else if (!process.argv[i].startsWith("-")) {
      options.action = process.argv[i];
    }
  }

  console.log("Action: ", red, options.action + "\n", reset);

  if (options.dump) {
    const stats = fs.lstatSync(options.dump);
    if (stats.isDirectory()) {
      throw new Error("CrossRef dump path must be a file, not a directory");
    } else if (!stats.isFile()) {
      throw new Error("CrossRef dump path must be a valid file");
    }
  }

  return options;
}

async function main() {
  try {
    const options = await init();
    processAction(options);
  } catch (err) {
    console.error(err);
  }
}

main();
