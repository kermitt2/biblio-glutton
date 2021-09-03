var elasticsearch = require('@elastic/elasticsearch');

var client = new elasticsearch.Client( {
    node: "http://localhost:9200/",
    //node: "http://192.168.1.106:9200",
    keepAlive: false,
    log: "error",
    requestTimeout: 1000000,  //to give more time for indexing
    sniffOnStart: true       //discover the rest of the cluster at startup
    // sniffOnConnectionFault: true,
    // sniffInterval: 300,
    //suggestCompression: true
});

module.exports = client;
