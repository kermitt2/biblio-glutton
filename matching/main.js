'use strict';

var client = require('./my_connection.js'),
    path = require('fs');

// for making console output less boring
const green = '\x1b[32m';
const red = '\x1b[31m';
const orange = '\x1b[33m';
const white = '\x1b[37m';
const blue = `\x1b[34m`;
const score = '\x1b[7m';
const bright = "\x1b[1m";
const reset = '\x1b[0m';

function processAction(options) {
    if (options.action === "health") {
        client.cluster.health({},function(err, resp, status) {  
            console.log("ES Health --", resp);
        });
    } else if ( (options.action === "index") and (options.force) ) {
        // remove previous index
        if (indexExists("crossref")) {
            deleteIndex("crossref");
        }

    } else if ( (options.action === "index") {

    }
}

/** 
 * Check if an index exists 
 */
function indexExists(indexName) {
    client.indices.exists({
        index: indexName
    }).then(function (resp) {
        console.log(resp);
        return resp;
    }, function (err) {
        console.log(err.message);
        return false;
    });
}

/** 
 * Create the index 
 */
function initIndex(indexName) {
    client.indices.create({
        index: indexName
    }).then(function (resp) {
        console.log(resp);
        res.status(200)
        return res.json(resp)
    }, function (err) {
        console.log(err.message);
        res.status(500)
        return res.json(err)
    });
}

/**
 * Load the analyzer and mapping to the index 
 */
function initMapping(indexName, docType, payload) {
    client.indices.putMapping({
        index: indexName,
        type: docType,
        body: payload
    }).then(function (resp) {
        console.log(resp);
        return true;
    }, function (err) {
        console.error(err.message);
        return false;
    });
}

/** 
 * Delete a document from an index
 */
function deleteDocument(indexName, _id, docType) {
    client.delete({
        index: indexName,
        type: docType,
        id: _id
    }, function(err, resp) {
        if (err) { 
            console.error(err.message);
            return false;
        } else {
            console.log(resp);
            return true;
        }
    });
}

/**
 * Delete the index
 */
function deleteIndex(indexName) {
    client.indices.delete({
        index: indexName
    }, function(err, resp) {
        if (err) {
            console.error(err.message);
            return false;
        } else {
            console.log(resp);
            console.log('Index ' + indexName + ' have been deleted', resp);
            return true;
        }
    });
}

/**
 * Init the main object with paths passed with the command line
 */
function init() {
    var options = new Object();
    
    // default service is full text processing
    options.action = "health";
    options.concurrency = 100; // number of concurrent call, default is 10
    options.force = false; // delete existing index and full re-indexing if true
    var attribute; // name of the passed parameter
    // get the path to the PDF to be processed
    for (var i = 2, len = process.argv.length; i < len; i++) {
        if (process.argv[i] == "-force") {
            options.force = true;
        } else if (process.argv[i-1] == "-dump") {
            options.dump = process.argv[i];
        } else if (!process.argv[i].startsWith("-")) {
            options.action = process.argv[i];
        }
    }

    console.log("\matching: ", red, options.action+"\n", reset);

    // check the dump path, if any
    if (options.dump) {
        fs.lstat(options.dump, (err, stats) => {
            if (err)
                console.log(err);
            if (stats.isDirectory())
                console.log("CrossRef dump path must be a file, not a directory");
            if (!stats.isFile()) 
                console.log("CrossRef dump path must be a valid file");
        });
    }

    return options;
}

function end() {
    var this_is_the_end = new Date() - start
    console.info('Execution time: %dms', this_is_the_end)
}

var start;

function main() {
    var options = init();
    start = new Date()
    process_action(options);
}

main();