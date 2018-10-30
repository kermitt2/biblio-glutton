//TODO: to be integrated into main.js once it works

let fs = require('fs');
let es = require('event-stream');
let lzma = require('lzma-native');
let async = require('async');

const elasticsearch = require('elasticsearch');

const batchSize = 10000;

//Create client
let client = new elasticsearch.Client({
    // hosts: ['localhost:9200', 'localhost:9201'],
    host: 'localhost:9200',
    log: 'error',
    keepAlive: true
    // sniffOnConnectionFault: true,
    // sniffOnStart: true,
    // sniffInterval: 300,
    // suggestCompression: true
});

// let analyserPath = "./resources/analyzer.json";
let analyserPath = "resources/analyzer.json";
let mappingPath = "resources/crossref_mapping.json";

let input = '/Volumes/SEAGATE1TB/scienceminer/crossref/crossref-works.2018-09-05.json.xz';

function createIndex(index) {
    var analyserFile = fs.readFileSync(analyserPath, 'utf-8').toString();
    var mappingFile = fs.readFileSync(mappingPath, 'utf-8').toString();

    client.indices
        .create(
            {
                index: index,
                ignore: [404, 400],
                mapping: mappingFile
            },
            function (err, resp) {
                if (err)
                    console.log('Failed to create ElasticSearch index, ' + err.message);
                else {
                    console.log('Successfully created ElasticSearch index');
                    // client.indexes.putMapping(mappingFile)
                }
            }
        );
}

function delete_index(index) {
    client.indices
        .delete(
            {
                index: index,
                ignore: [404]
            },
            function (error, response) {
                if (error) {
                    console.log("Error deleting index: " + error)
                }
            }
        );
}

async function init_cluster(index) {
    try {
        await client.ping({
            requestTimeout: 30000,
        }).then(function (body) {
            console.log("Cluster alive");
        }, function (error) {
            console.log("Cannot contact cluster: " + error);
            process.exit(-1);
        });

        await delete_index(index);
        await createIndex(index);
        console.log('Init done.')
    } catch (err) {
        console.log(err)
    }
}

init_cluster("crossref")
    .then(body => {
            console.log("Loading file " + input);
            load_file(input);
        }
    );


function load_file(input) {
    var start = new Date();
    readStreamBao = fs.createReadStream(input)
        .pipe(lzma.createDecompressor())
        .pipe(es.split())
        .pipe(es.map(function (data, cb) {
            // prepare/massage the data

            // - migrate id from '_id' to 'id'
            var obj = JSON.parse(data);
            obj.id = obj._id.$oid;
            delete obj._id;

            // - remove citation data
            delete obj.reference;

            cb(null, obj)
        }));

    async.series([
        function (next) {
            var i = 0;
            var batch = [];

            readStreamBao.on("data", function (doc) {
                // console.log('indexing %s', doc.id);
                batch.push({
                    index: {
                        "_index": 'crossref',
                        "_type": 'work',
                        "_id": doc.id.toString()
                    }
                });

                // var obj;
                // if (doc.toObject) {
                //     obj = doc.toObject();
                // } else {
                //     obj = doc;
                // }
                // obj = _.clone(obj);

                // delete obj._id;
                batch.push(doc);
                i++;
                if (i % batchSize == 0) {
                    let intermediate_time = (new Date() - start) / 1000;
                    console.log('Loaded %s records in %dms s (%dms record/s)', i, intermediate_time, batchSize / intermediate_time);
                    client.bulk({
                        refresh: "false", //we do refresh only at the end
                        body: batch
                    }, function (err, resp) {
                        if (err) {
                            next(err);
                        } else if (resp.errors) {
                            next(resp);
                        }
                    });
                    batch = [];
                }
            });

            // When the stream ends write the remaining records
            readStreamBao.on("end", function () {
                if (batch.length > 0) {
                    console.log('Loaded %s records', batch.length);
                    client().bulk({
                        refresh: "wait_for", // we wait for this last batch before refreshing
                        body: batch
                    }, function (err, resp) {
                        if (err) {
                            console.log(err, 'Failed to build index');
                            next(err);
                        } else if (resp.errors) {
                            console.log(resp.errors, 'Failed to build index');
                            next(resp);
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
        }]);

    /*.pipe(es.map(function (line, cb) {

        var response = undefined;
        try {

            response = client.index({
                index: 'crossref',
                type: 'item',
                body: line
            });

        } catch (error) {
            console.trace(error)
        }
        cb(null, response)
    }))
    .on('error',
        function (error) {
            console.log("Error occurred: " + error);
        }
    )
    .on('finish',
        function () {
            console.log("Finished. ")
        }
    );*/
}


