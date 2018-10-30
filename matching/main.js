'use strict';

var client = require('./my_connection.js'),
    fs = require('fs'),
    lzma = require('lzma-native'),
    es = require('event-stream'),
    async = require("async");

// for making console output less boring
const green = '\x1b[32m';
const red = '\x1b[31m';
const orange = '\x1b[33m';
const white = '\x1b[37m';
const blue = `\x1b[34m`;
const score = '\x1b[7m';
const bright = "\x1b[1m";
const reset = '\x1b[0m';

const analyserPath = "resources/analyzer.json";
const mappingPath = "resources/crossref_mapping.json";

//TODO: move them in the configuration / parameters if convenient
const index_name = "crossref";
const doc_type = "work";
const batchSize = 1000;

function processAction(options) {
    if (options.action === "health") {
        client.cluster.health({}, function (err, resp, status) {
            console.log("ES Health --", resp);
        });
    } else if ((options.action === "index") && (options.force)) {
        // remove previous index
        console.log("force index");

        async.waterfall([
            function indexExists(callback) {
                console.log("indexExists");
                client.indices.exists({
                    index: index_name
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
                        index: index_name
                    }, function (err, resp, status) {
                        if (err) {
                            console.error('deleteIndex error: ' + err.message);
                            return callback(err);
                        } else {
                            console.log('Index crossref have been deleted', resp);
                            return callback(null, false);
                        }
                    });
                } else {
                    return callback(null, false);
                }
            },
            function createIndex(existence, callback) {
                console.log("createIndex");
                var analyzers;
                try {
                    analyzers = fs.readFileSync(analyserPath, 'utf8');
                } catch (e) {
                    console.log('error reading analyzer file ' + e);
                }

                if (!existence) {
                    client.indices.create({
                        index: index_name,
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
                var mapping;
                try {
                    mapping = fs.readFileSync(mappingPath, 'utf8');
                } catch (e) {
                    console.log('error reading mapping file ' + e);
                }

                // put the mapping now
                client.indices.putMapping({
                    index: index_name,
                    type: doc_type,
                    body: mapping
                }, function (err, resp, status) {
                    if (err) {
                        console.log('mapping error: ' + err.message);
                    } else
                        console.log("mapping loaded");
                    return callback(null, true);
                });
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
    delete jsonObj.reference;
    delete jsonObj.abstract;

    return jsonObj;
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
            obj.id = data._id.$oid;
            delete data._id;

            // just keep the fields we want to index
            obj.title = data.title;
            obj.DOI = data.DOI;
            // ... 

            // store the whole json doc in a field, to avoid further parsing it during indexing
            obj.jsondoc = JSON.stringify(data);

            //console.log(obj);

            cb(null, obj)
        }))

        /*        .pipe(es.map(function (jsonObj, cb) {
                    var response = undefined;
                    try {
                        var localId = jsonObj._id;
                        delete jsonObj._id;
                        response = client.index({
                            index: index_name,
                            type: doc_type,
                            id: localId,
                            body: jsonObj
                        });
                    } catch (error) {
                        console.trace(error)
                    }
                    cb(null, response)
                }))*/
        .on('error',
            function (error) {
                console.log("Error occurred: " + error);
            }
        )
        .on('finish',
            function () {
                console.log("Finished. ")
            }
        );


    async.series(
        [
            function (next) {
                var i = 0;
                var batch = [];
                var previous_end = start;

                readStream.on("data", function (doc) {
                    // console.log('indexing %s', doc.id);
                    batch.push({
                        index: {
                            "_index": 'crossref',
                            "_type": 'work',
                            "_id": doc.id.toString()
                        }
                    });

                    batch.push(doc);
                    i++;
                    if (i % batchSize === 0) {
                        let end = new Date();
                        let total_time = (end - start) / 1000;
                        let intermediate_time = (end - previous_end) / 1000;
                        console.log('Loaded %s records in %d s (%d record/s)', i, total_time, batchSize / intermediate_time);
                        client.bulk(
                            {
                                refresh: "wait_for", //we do refresh only at the end
                                body: batch
                            },
                            function (err, resp) {
                                if (err) {
                                    throw err;
                                } else if (resp.errors) {
                                    throw resp;
                                }
                            }
                        );
                        batch = [];
                        previous_end = end;
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
        ],
        function (err, results) {
            if (err)
                console.log(err);
            else
                console.log(results);
        }
    );
}

/**
 * Init the main object with paths passed with the command line
 */
function init() {
    var options = new Object();

    options.action = "health";
    options.concurrency = 100; // number of concurrent call, default is 10
    options.force = false; // delete existing index and full re-indexing if true
    var attribute; // name of the passed parameter

    for (var i = 2, len = process.argv.length; i < len; i++) {
        if (process.argv[i] === "-force") {
            options.force = true;
        } else if (process.argv[i - 1] === "-dump") {
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