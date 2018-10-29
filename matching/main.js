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

function process_action(options) {
    if (options.action === "health") {
        client.cluster.health({},function(err, resp, status) {  
            console.log("ES Health --", resp);
        });
    }
}

/**
 * Init the main object with paths passed with the command line
 */
function init() {
    var options = new Object();
    
    // default service is full text processing
    options.action = "health";
    options.concurrency = 100; // number of concurrent call, default is 10
    var attribute; // name of the passed parameter
    // get the path to the PDF to be processed
    for (var i = 2, len = process.argv.length; i < len; i++) {
        if (process.argv[i-1] == "-dump") {
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