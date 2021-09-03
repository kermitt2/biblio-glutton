'use strict';

var client = require('./my_connection.js'),
    fs = require('fs'),
    path = require('path'),
    lzma = require('lzma-native'),
    es = require('event-stream'),
    async = require("async"),
    sleep = require('sleep'),
    zlib = require('zlib'),
    process = require('process');

// for making console output less boring
const green = '\x1b[32m';
const red = '\x1b[31m';
const orange = '\x1b[33m';
const white = '\x1b[37m';
const blue = `\x1b[34m`;
const score = '\x1b[7m';
const bright = "\x1b[1m";
const reset = '\x1b[0m';

const settingsPath = "resources/settings.json";
const mappingPath = "resources/crossref_mapping.json";

// basic global counter
var indexed = 0;
function incrementIndexed(amount) {
    indexed += amount;
    return indexed;
};

function processAction(options) {
    if (options.action === "health") {
        client.cluster.health({}, function (err, resp, status) {
            console.log("ES Health --", resp);
        });
    } else if ((options.action === "index")) {
        // remove previous index if it exists
        async.waterfall([
            function indexExists(callback) {
                client.indices.exists({
                    index: options.indexName
                }, function (err, resp, status) {
                    if (err) {
                        console.error('indexExists error: ' + err.message);
                        return callback(err);
                    }
                    return callback(null, resp.body);
                });
            },
            function deleteIndex(existence, callback) {
                if (existence) {
                    client.indices.delete({
                        index: options.indexName
                    }, function (err, resp, status) {
                        if (err) {
                            console.error('deleteIndex error: ' + err.message);
                            return callback(err);
                        } 
                        console.log(orange, 'Existing index crossref have been deleted', reset);
                        return callback(null, false);
                    });
                } else {
                    return callback(null, false);
                }
            },
            function createIndex(existence, callback) {
                var analyzers;
                var mapping;
                try {
                    analyzers = fs.readFileSync(settingsPath, 'utf8');
                    mapping = fs.readFileSync(mappingPath, 'utf8');
                } catch (e) {
                    console.error('error reading analyzer/mapping file ' + e);
                }

                if (!existence) {
                    var request_body = {
                        "settings": JSON.parse(analyzers),
                        "mappings": JSON.parse(mapping)
                    };

                    client.indices.create({
                        index: options.indexName,
                        body: request_body
                    }, function (err, resp, status) {
                        if (err) {
                            console.error('createIndex error: ' + err.message);
                            return callback(err)
                        }
                        console.log(green, 'Index crossref have been created\n', reset);
                        return callback(null, true);
                    });
                }

            }
        ], (err, results) => {
            if (err) {
                console.error('setting error: ' + err);
            }

            if (options.action === "index") {
                // launch the heavy indexing stuff...
                index(options);
            }
        })
    }

}

function parseJson(data) {
    // special cleaning for the academic torrent dump, others are in jsonl format
    data = data.replace('\n]\n}\n', '');
    if (data.startsWith('{"items":['))
        data = data.replace('{"items":[', '');
    
    var jsonObj = JSON.parse(data);
    return jsonObj;
}

function createBiblObj(data, cb) {
    // prepare/massage the data
    data = parseJson(data);
    var obj = new Object();

    // - migrate id from '_id' to 'id'
    if ('_id' in data && '$oid' in data._id) {
        obj._id = data._id.$oid;
        delete data._id;
    }

    // Just keep the fields we want to index

    // - Main fields (in the mapping)
    obj.title = data.title;
    obj.DOI = data.DOI;

    if (data.author) {
        obj.author = "";
        var firstAuthorSet = false;
        for (var aut in data.author) {
            if (data.author[aut].sequence === "first") {
                if (data.author[aut].family) {
                    obj.first_author = data.author[aut].family;
                    firstAuthorSet = true;
                }
                /*else {
                    obj.first_author = data.author[aut].name;
                    console.log(data.author[aut]);
                }*/
            }
            if (data.author[aut].family)
                obj.author += data.author[aut].family + " ";
            /*else 
                console.log(data.author[aut]);*/
        }
        obj.author = obj.author.trim();

        if (!firstAuthorSet) {
            // not sequence information apparently, so as fallback we use the first
            // author in the author list
            if (data.author.length > 0) {
                if (data.author[0].family) {
                    obj.first_author = data.author[0].family;
                    firstAuthorSet = true;
                }
            }
        }
    }

    // parse page metadata to get the first page only
    if (data.page) {
        var pagePieces = data.page.split(/,|-| /g);
        if (pagePieces && pagePieces.length > 0) {
            obj.first_page = pagePieces[0];
            //console.log(data.page, obj.first_page);
        }
    }

    obj.journal = data['container-title'];
    if ('short-container-title' in data)
        obj.abbreviated_journal = data['short-container-title'];

    obj.volume = data.volume;
    if (data.issue) {
        obj.issue = data.issue;
    }

    // year is a date part (first one) in issued or created or published-online (we follow this order)
    if (data.issued) {
        if (data.issued['date-parts']) {
            obj.year = data.issued['date-parts'][0][0]
        }
    }
    if (!obj.year && data['published-online']) {
        if (data['published-online']['date-parts']) {
            obj.year = data['published-online']['date-parts'][0][0]
        }
    }
    if (!obj.year && data['published-print']) {
        if (data['published-print']['date-parts']) {
            obj.year = data['published-print']['date-parts'][0][0]
        }
    }
    // this is deposit date, normally we will never use it, but it will ensure 
    // that we always have a date as conservative fallback
    if (!obj.year && data.created) {
        if (data.created['date-parts']) {
            obj.year = data.created['date-parts'][0][0]
        }
    }
    //console.log(obj.year);

    // bibliograohic field is the concatenation of usual bibliographic metadata
    var biblio = buildBibliographicField(obj);
    if (biblio && biblio.length > 0) {
        obj.bibliographic = biblio;
    }

    obj.type = data.type;

    cb(null, obj)
}

function buildBibliographicField(obj) {
    var res = "";

    if (obj.author)
        res += obj.author;
    else if (obj.first_author)
        res += obj.first_author;

    if (obj.title)
        res += " " + obj.title;

    if (obj.journal)
        res += " " + obj.journal;

    if (obj.abbreviated_journal)
        res += " " + obj.abbreviated_journal;

    if (obj.volume)
        res += " " + obj.volume;

    if (obj.issue)
        res += " " + obj.issue;

    if (obj.first_page)
        res += " " + obj.first_page;

    if (obj.year)
        res += " " + obj.year;

    return res.trim();
}

function filterType(doc) {
    // if document type is component, we ignore it (it means that the DOI is 
    // for a sub-part of a publication, like 10.1371/journal.pone.0104614.t002)
    if (doc.type === "component") {
        return true;
    }
    return false;
}

function index(options) {
    if (options.dumpType === 'directory') {
        const files = fs.readdirSync(options.dump)
        console.log(orange, 'total of '+files.length+' files to be indexed\n', reset);
        for (const file of files) {

            if (!file.endsWith(".gz")) {
                console.log(orange, path.basename(file) + " is not an expected *.json.gz file, skipping...", reset);
                continue;
            }
            const file_path = path.join(options.dump, file);
            indexFile(options, file_path);
        }
    } else {
        indexFile(options, options.dump);
    }
}

function indexFile(options, dumpFile) {
    // look at the dump
    var readStream = null;
    if (options.dumpType === 'xz') {
        readStream = fs.createReadStream(dumpFile)
            .pipe(lzma.createDecompressor())
            .pipe(es.split())
            .pipe(es.map(createBiblObj))
            .on('error',
                function (error) {
                    console.log("Error occurred: " + error);
                }
            );
    } else if (options.dumpType === 'gz') {
        readStream = fs.createReadStream(dumpFile)
            .pipe(zlib.createGunzip())
            .pipe(es.split())
            .pipe(es.map(createBiblObj))
            .on('error',
                function (error) {
                    console.log("Error occurred: " + error);
                }
            );
    } else if (options.dumpType === 'json') {
        // we assume here it's a jsonl file, not a mega huge array
        readStream = fs.createReadStream(options.dump)
            .pipe(es.split())
            .pipe(es.map(function (data, cb) {
                createBiblObj(data, cb);
            }))
            .on('error',
                function (error) {
                    console.log("Error occurred: " + error);
                }
            );
    } else if (options.dumpType === 'directory') {
        // note: it's not jsonl, we have a json on each line, but in a global array 
        readStream = fs.createReadStream(dumpFile)
            .pipe(zlib.createGunzip())
            .pipe(es.split(",\n"))
            .pipe(es.map(createBiblObj))
            .on('error',
                function (error) {
                    console.log("Error occurred: " + error);
                });
    } else {
        console.log('Unsupported dump format: must be uncompressed json, compressed json file (xz, gzip) or directory of *.json.gz files.');
    }

    if (readStream == null)
        return;

    var i = 0;
    var batch = [];
    var previous_end = start;

    readStream.on("data", function (doc) {
        //console.log("indexing %s", doc.id);

        if (doc == null)
            return;

        // filter some type of DOI not corresponding to a publication (e.g. component of a publication)
        if (filterType(doc)) {
            return;
        }
        
        var localId = doc.DOI.toLowerCase();
        if ("_id" in doc) {
            localId = doc._id;
            delete doc._id;
        }
        delete doc.type;

        batch.push({
            index: {
                _index: options.indexName,
                _id: localId
            }
        });

        batch.push(doc);
        i++;

        if (i % options.batchSize === 0) {
            var previous_start = new Date();

            async.waterfall([
                function (callback) {
                    client.bulk(
                        {
                            refresh: "false", //we do refresh only at the end
                            //requestTimeout: 200000,
                            body: batch
                        },
                        function (err, resp) { 
                            if (err) { 
                                console.log(err.message);
                                throw err;
                            } else if (resp.errors) {
                                console.log('Bulk is rejected... let\'s medidate 10 seconds about the illusion of time and consciousness');
                                // let's just wait and re-send the bulk request with increased
                                // timeout to be on the safe side
                                console.log("Waiting for 10 seconds");
                                sleep.msleep(20000); // -> this is blocking... time for elasticsearch to do whatever it does
                                // and be in a better mood to accept this bulk
                                client.bulk(
                                    {
                                        refresh: "false",
                                        //requestTimeout: 200000,
                                        body: batch
                                    },
                                    function (err, resp) { 
                                        if (err) { 
                                            console.log(err.message);
                                            throw err;
                                        } else if (resp.errors) {
                                            console.log(resp);
                                            // at this point it's hopeless ?
                                            throw resp;
                                            // alternative would be to block again and resend
                                            // propagate that in a next function of the async to have something less ugly?
                                        }
                                        console.log("bulk is finally ingested...");
                                        let theEnd = new Date();
                                        return callback(null, theEnd);
                                    });
                            } else {
                                let theEnd = new Date();
                                return callback(null, theEnd);
                            }
                        });
                },
                function(end, callback) {
                    let total_time = (end - start) / 1000;
                    let intermediate_time = (end - previous_start) / 1000;

                    console.log('Loaded %s records in %d s (%d record/s)', incrementIndexed(options.batchSize), 
                        total_time, options.batchSize / intermediate_time);
                    return callback(null, total_time);
                }
            ],
            function (err, total_time) {
                if (err)
                    console.log(err);
            });

            batch = [];
            i = 0;
        }
    });

    // When the stream ends write the remaining records
    readStream.on("end", function () {
        if (batch.length > 0) {
            //console.log('Loaded %s records', batch.length);
            client.bulk({
                refresh: "false", // refreshing is done at the very end given that we can have multiple files in the crossref dump
                body: batch
            }, function (err, resp) {
                if (err) {
                    console.log(err, 'Failed to build index');
                    throw err;
                } else if (resp.errors) {
                    console.log(resp.errors, 'Failed to build index');
                    throw resp;
                } else {
                    console.log('Completed indexing of CrossRef dump file, %d record', incrementIndexed(options.length));
                }
            });
        } 
        batch = [];
    });
}

function refreshIndex(e) {
    var options = init();
    // final refresh of the index, to be called just before leaving
    client.indices.refresh( { index:options.indexName }, function (err, resp) {
        if (err) {
            console.log(err, red, 'Failed to refresh index', reset);
            throw err;
        } else if (resp.errors) {
            console.log(resp.errors, red, 'Failed to refresh index', reset);
            throw resp;
        } else {
            console.log(green, "\nAll tasks completed !\n", reset);
            end();
            process.exit(); 
        }
    }); 
}

/**
 * Init the main object with paths passed with the command line
 */
function init() {
    var options = new Object();

    // first get the config
    const config = require('./config.json');
    options.indexName = config.indexName;
    options.docType = config.docType;
    options.batchSize = config.batchSize;

    options.action = "health";
    var attribute; // name of the passed parameter

    for (var i = 2, len = process.argv.length; i < len; i++) {
        if (process.argv[i - 1] === "-dump") {
            options.dump = process.argv[i];
        } else if (!process.argv[i].startsWith("-")) {
            options.action = process.argv[i];
        }
    }

    // check the dump path, if present
    if (options.dump) {
        const stats = fs.lstatSync(options.dump);
        if (stats.isDirectory()) {
            options.dumpType = "directory"
        } else if (stats.isFile()) {
            if (options.dump.endsWith(".json")) {
                options.dumpType = "json"
            } else if (options.dump.endsWith(".gz")) {
                options.dumpType = "gz"
            } else if (options.dump.endsWith(".xz")) {
                options.dumpType = "xz"
            } else {
                throw new Error("The dump format is not recognised ");
            }
        } else {
            throw new Error("CrossRef dump path must be a valid file or directory");
        }
    }

    return options;
}

function end() {
    var this_is_the_end = new Date() - start;
    console.info('Execution time: %dms', this_is_the_end)
}

var start;

// an pre-exit function that ensure that the index is refreshed and functional just before leaving...
process.on('beforeExit', refreshIndex);

function main() {
    var options = init();
    console.log("\naction: ", green, options.action + "\n", reset);

    start = new Date();
    processAction(options);
}

main();
