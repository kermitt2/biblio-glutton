var elasticsearch = require('elasticsearch');

var client = new elasticsearch.Client( {  
  hosts: [
    'https://[username]:[password]@[server]:[port]/',
    'https://[username]:[password]@[server]:[port]/'
  ]
});

module.exports = client;  