var elasticsearch = require('elasticsearch');

var client = new elasticsearch.Client( {  
    host: 'http://localhost:9200/'
});

module.exports = client;  