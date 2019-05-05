var elasticsearch = require('elasticsearch');

var client = new elasticsearch.Client( {
    // hosts: ['localhost:9200', 'localhost:9201'],
    host: 'http://elasticsearch:9200/',
    keepAlive: false,
    log: "error",
    requestTimeout: 1000000,  //to give more time for indexing
    sniffOnStart: true,      //discover the rest of the cluster at startup
    // sniffOnConnectionFault: true,
    // sniffInterval: 300,
    suggestCompression: true
});

module.exports = client;
