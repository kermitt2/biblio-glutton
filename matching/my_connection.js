var elasticsearch = require('elasticsearch');

var client = new elasticsearch.Client( {
    // hosts: ['localhost:9200', 'localhost:9201'],
    host: 'http://localhost:9200/',
    keepAlive: true,
    log: "error",
    requestTimeout: 100000,  //to give more time for indexing
    sniffOnStart: true,      //discover the rest of the cluster at startup
    // sniffOnConnectionFault: true,
    // sniffInterval: 300,
    suggestCompression: true
});

module.exports = client;
