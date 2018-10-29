//TODO: to be integrated into main.js once it works

let fs = require('fs');
let es = require('event-stream');
let lzma = require('lzma-native');

const elasticsearch = require('elasticsearch');

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

function print(body) {
    process.stdout.write(body + "\n");
}

//Test connection
client.ping({
    requestTimeout: 30000,
}).then(function (body) {
    print("Cluster alive");
}, function (error) {
    print("Cannot contact cluster: " + error);
});

let analyserFile = "resources/analyzer.json";
let mappingFile = "resources/crossref_mapping.json";

let input = '/Volumes/SEAGATE1TB/scienceminer/crossref/crossref-works.2018-09-05.json.xz';

client.indices
    .delete(
        {
            index: "crossref",
            ignore: [404]
        },
        function (error, response) {
            if (error) {
                print("Error deleting index: " + error)
            }
        }
    );

client.indices
    .create(
        {
            index: 'crossref',
            ignore: [404, 400]
        },
        function (err, resp) {
            if (err)
                print('Failed to create ElasticSearch index, ' + err.message);
            else
                print('Successfully created ElasticSearch index');
        }
    );


fs.createReadStream(input)
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
    }))
    .pipe(es.map(function (line, cb) {

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
    .on('error', function (error) {
        process.stderr.write("Error occurred: " + error);
    })
    .on('finish', function () {
        process.stdout.write("Finished. ")
    });



