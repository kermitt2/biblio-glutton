var elasticsearch = require('elasticsearch');

var client = new elasticsearch.Client( {
    host: 'http://localhost:9200/',
    sniffOnStart: true,    //discover the rest of the cluster at startup
    log: "error"
});

module.exports = client;  