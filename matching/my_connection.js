var elasticsearch = require('elasticsearch');

var client = new elasticsearch.Client( {
    // hosts: ['localhost:9200', 'localhost:9201'],
    host: 'http://localhost:9200/',
    keepAlive: true,
    log: "error",
    sniffOnStart: true    //discover the rest of the cluster at startup
    // sniffOnConnectionFault: true,
    // sniffInterval: 300,
    // suggestCompression: true
});

module.exports = client;
