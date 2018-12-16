'use strict';

var client = require('./my_connection.js'),
    fs = require('fs'),
    lzma = require('lzma-native'),
    es = require('event-stream'),
    async = require("async"),
    sleep = require('sleep');

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

function processAction(options) {
    if (options.action === "health") {
        client.cluster.health({}, function (err, resp, status) {
            console.log("ES Health --", resp);
        });
    } else if ((options.action === "index")) {
        // remove previous index if it exists
        async.waterfall([
            function indexExists(callback) {
                console.log("indexExists");
                client.indices.exists({
                    index: options.indexName
                }, function (err, resp, status) {
                    if (err) {
                        console.log('indexExists error: ' + err.message);
                        return callback(err);
                    }
                    console.log("indexExists: ", resp);
                    return callback(null, resp);
                });
            },
            function deleteIndex(existence, callback) {
                console.log("deleteIndex: " + existence);
                if (existence) {
                    client.indices.delete({
                        index: options.indexName
                    }, function (err, resp, status) {
                        if (err) {
                            console.error('deleteIndex error: ' + err.message);
                            return callback(err);
                        } 
                        console.log('Index crossref have been deleted', resp);
                        return callback(null, false);
                    });
                } else {
                    return callback(null, false);
                }
            },
            function createIndex(existence, callback) {
                console.log("createIndex");
                var analyzers;
                try {
                    analyzers = fs.readFileSync(settingsPath, 'utf8');
                } catch (e) {
                    console.log('error reading analyzer file ' + e);
                }

                if (!existence) {
                    client.indices.create({
                        index: options.indexName,
                        body: analyzers
                    }, function (err, resp, status) {
                        if (err) {
                            console.log('createIndex error: ' + err.message);
                            return callback(err)
                        }
                        console.log('createIndex: ', resp);
                        return callback(null, true);
                    });
                }

            },
            function addMappings(existence, callback) {
                console.log("addMappings");
                var mapping;
                try {
                    mapping = fs.readFileSync(mappingPath, 'utf8');
                } catch (e) {
                    console.log('error reading mapping file ' + e);
                }

                // put the mapping now
                if (existence) {
                    client.indices.putMapping({
                        index: options.indexName,
                        type: options.docType,
                        body: mapping
                    }, function (err, resp, status) {
                        if (err) {
                            console.log('mapping error: ' + err.message);
                            return callback(err)
                        } 
                        console.log("mapping loaded");
                        return callback(null, true);
                    });
                }
            }
        ], (err, results) => {
            if (err) {
                console.log('setting error: ' + err);
            }

            if (options.action === "index") {
                // launch the heavy indexing stuff...
                index(options);
            }
        })
    }

}

/**
 * This function removes some non-used stuff from a crossref work entry,
 * in particular the citation information, which represent a considerable
 * amount of data.
 */
function massage(data) {
    var jsonObj = JSON.parse(data);
    return jsonObj;
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
    var readStream = fs.createReadStream(options.dump)
        .pipe(lzma.createDecompressor())
        .pipe(es.split())
        .pipe(es.map(function (data, cb) {
            // prepare/massage the data
            //console.log(data);
            data = massage(data);
            var obj = new Object();

            // - migrate id from '_id' to 'id'
            obj._id = data._id.$oid;
            delete data._id;

            // Just keep the fields we want to index

            // - Main fields (in the mapping)
            obj.title = data.title;
            obj.DOI = data.DOI;

            if (data.author) {
                obj.author = "";
                for (var aut in data.author) {
                    if (data.author[aut].sequence === "first") {
                        if (data.author[aut].family)
                            obj.first_author = data.author[aut].family;
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
            if (!obj.year && data.created) {
                if (data.created['date-parts']) {
                    obj.year = data.created['date-parts'][0][0]
                }
            }
            if (!obj.year && data['published-online']) {
                if (data['published-online']['date-parts']) {
                    obj.year = data['published-online']['date-parts'][0][0]
                }
            }
            //console.log(obj.year);

            // bibliograohic field is the concatenation of usual bibliographic metadata
            var biblio = buildBibliographicField(obj);
            if (biblio && biblio.length > 0) {
                obj.bibliographic = biblio;
            }

            obj.type = data.type;

            // store the whole json doc in a field, to avoid further parsing it during indexing
            /*let z = JSON.stringify(data);
            obj.jsondoc = [];

            let bytesLength = Buffer.byteLength(z, 'utf8');
            if (bytesLength > 30000) {
                console.log(obj._id);
                console.log(obj.DOI);
                console.log("bytesLength:", bytesLength);
            }
            let buffer = Buffer.from(z, 'utf8');
            var number_chunks_required = Math.ceil(bytesLength / 30000);
            if (bytesLength > 30000) {
                console.log("number_chunks_required:", number_chunks_required);
            }
            for (var i = 0; i < number_chunks_required; i++) {
                obj.jsondoc.push(buffer.toString('utf8', (i * 30000), (i + 1) * 30000));
            }*/

            cb(null, obj)
        }))
        .on('error',
            function (error) {
                console.log("Error occurred: " + error);
            }
        )
        .on('finish',
            function () {
                console.log("Finished. ");
                end();
            }
        );

    var i = 0;
    var indexed = 0;
    var batch = [];
    var previous_end = start;

    readStream.on("data", function (doc) {
        // console.log('indexing %s', doc.id);

        // filter some type of DOI not corresponding to a publication (e.g. component
        // of a publication)
        if (!filterType(doc)) {
            var localId = doc._id;
            delete doc._id;
            delete doc.type;
            batch.push({
                index: {
                    _index: options.indexName,
                    _type: options.docType,
                    _id: localId
                }
            });

            batch.push(doc);
            i++;
        } else {
            return ;
        }


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

                    indexed += options.batchSize;
                    console.log('Loaded %s records in %d s (%d record/s)', indexed, total_time, options.batchSize / intermediate_time);
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
            console.log('Loaded %s records', batch.length);
            client().bulk({
                refresh: "true", // we wait for this last batch before refreshing
                body: batch
            }, function (err, resp) {
                if (err) {
                    console.log(err, 'Failed to build index');
                    throw err;
                } else if (resp.errors) {
                    console.log(resp.errors, 'Failed to build index');
                    throw resp;
                } else {
                    console.log('Completed crossref indexing.');
                    next();
                }
            });
        } else {
            next();
        }

        batch = [];
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
    options.concurrency = 100; // number of concurrent call, default is 10
    //options.force = false; // delete existing index and full re-indexing if true
    var attribute; // name of the passed parameter

    for (var i = 2, len = process.argv.length; i < len; i++) {
        /*if (process.argv[i] === "-force") {
            options.force = true;
        } else*/ 
        if (process.argv[i - 1] === "-dump") {
            options.dump = process.argv[i];
        } else if (!process.argv[i].startsWith("-")) {
            options.action = process.argv[i];
        }
    }

    console.log("action: ", red, options.action + "\n", reset);

    // check the dump path, if any
    if (options.dump) {
        fs.lstat(options.dump, (err, stats) => {
            if (err)
                console.log(err);
            if (stats.isDirectory())
                console.log("CrossRef dump path must be a file, not a directory");
            if (!stats.isFile())
                console.log("CrossRef dump path must be a valid file");
        });
    }

    return options;
}

function end() {
    var this_is_the_end = new Date() - start;
    console.info('Execution time: %dms', this_is_the_end)
}

var start;

function main() {
    var options = init();
    start = new Date();
    processAction(options);
}

main();