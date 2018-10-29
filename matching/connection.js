var elasticsearch = require('elasticsearch');

var client = new elasticsearch.Client( {  
    hosts: [
        'https://[username]:[password]@[server]:[port]/',
        'https://[username]:[password]@[server]:[port]/'
    ],
    sniffOnStart: true,    // discover the rest of the cluster at startup
    log: "error"
});

module.exports = client;  