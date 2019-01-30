var http = require('http'),
    fs = require('fs'),
    zlib = require('zlib'),
    readline = require('readline');

// biblio-glutton server
var host = "localhost"
var port = "8080"
var service = "/service/oa?doi="

/**
 *  usage: 
 *
 *  > npm install
 *
 *  > node oa_coverage -istex ../data/istex/istexIds.all.gz
 *  > node oa_coverage -dump ../data/istex/dois.gz
 *  > node oa_coverage -pmc ../data/pmc/PMID_PMCID_DOI.csv.gz
 *
 *  (dump files are gzipped)
 */

function processDump(options, callback) {
    // read dump file line by line, get the DOI, check the OA 
    // availability of the DOI via biblio-glutton
    var file_path;
    if (options.istex_path)
        file_path = options.istex_path;
    else if (options.pmc_path)
        file_path = options.pmc_path;
    else
        file_path = options.dump_path;

    //console.log("processing... ", file_path);

    let rstream = readline.createInterface({
        input: fs.createReadStream(file_path).pipe(zlib.createGunzip())
    });

    //var rstream = fs.createReadStream(options.file_path).pipe(zlib.createGunzip());
    var total = 0; // total entries
    var total_oa = 0; // total open access available
    rstream.on('line', function(line) {
        var doi;
        var pmid;
        if (options.istex_path) {
            total++;
            var json = JSON.parse(line);
            if (json.doi && json.doi.length > 0) {
                doi = json.doi[0]
            }
        } else if (options.pmc_path) {
            if (line != 'PMID,PMCID,DOI') {
                // PMID,PMCID,DOI
                var pieces = line.split(",");
                pmid = pieces[0]
                var pmcid = pieces[1]
                doi = pieces[2]
                if (pmid && pmid.length > 0) {
                    total++;
                    if (doi && doi.length > 0) {
                        doi = doi.substring(1,doi.length-1)
                        if (doi.length == 0)
                            doi = null;
                        else
                            doi = doi.replace("https://doi.org/", "");
                    } else
                        doi = null;
                } else
                    doi = null;
            }
        } else {
            total++;
            var pieces = line.split(",");
            if (pieces.length == 2) {
                doi = pieces[0];
                if (doi.length > 1)
                    doi = doi.substring(1, doi.length-1);
                else 
                    doi = null;
            }
        }   

        if (doi) {
            var url = "http://" + options.glutton_host + ":" + options.glutton_port + options.service + doi; 
            //console.log(url);
            var request = http.get(url, function(response) {
                //console.log(response.statusCode);
                if (response.statusCode == 200) {
                    response.on("data", function(chunk) {
                        //console.log("[OA]: " + chunk);
                        if (pmid)
                            console.log('{ "doi":"' + doi + '", "pmid": "' + pmid + '", "best_oa_location" : { "url_for_pdf": "' + chunk + '"} }');
                        else
                            console.log('{ "doi":"' + doi + '", "best_oa_location" : { "url_for_pdf": "' + chunk + '"} }');                            
                        total_oa++;
                    });
                }
            }).on('error', function(err) { // Handle errors
                console.error('error calling glutton service:', doi); 
                // let's try again
                /*var request = http.get(url, function(response) {
                    //console.log(response.statusCode);
                    if (response.statusCode == 200) {
                        response.on("data", function(chunk) {
                            //console.log("[OA]: " + chunk);
                            if (pmid)
                                console.log('{ "doi":"' + doi + '", "pmid": "' + pmid + '", "best_oa_location" : { "url_for_pdf": "' + chunk + '"} }');
                            else
                                console.log('{ "doi":"' + doi + '", "best_oa_location" : { "url_for_pdf": "' + chunk + '"} }');                            
                            total_oa++;
                        })
                    }
                }).on('error', function(err) { // Handle errors
                    console.log('error calling glutton service:', doi); 
                });*/
            });
        }
    });
    rstream.on('finish', function (err) {
        if (err) { 
            console.error('error reading istex dump file', err)
        } 

        console.log("open access ratio: " + total_oa / total);

        if (callback)
            callback();
    });
}


function callGlutton(doi, callback) {
    var request = https.get(pdf_url, function(response) {
        response.pipe(file);
        file.on('finish', function() {
            file.close(callGROBID(options, istexId, callback));  
            // close() is async, call grobid after close completes.
        });
    }).on('error', function(err) { // Handle errors
        fs.unlink(dest, function(err2) { if (err2) { 
                return console.log('error removing downloaded PDF file'); 
            } 
        }); 
        // delete the file async
        if (callback) 
            callback(err.message);
    });

    if (callback)
        callback();
}

/**
 * Init the main object with paths passed with the command line
 */
function init() {
    var options = new Object();

    // start with the config file
    options.glutton_host = host;
    options.glutton_port = port;
    options.service = service;

    // default service is full text processing
    var attribute; // name of the passed parameter
    // get the path to the PDF to be processed
    for (var i = 2, len = process.argv.length; i < len; i++) {
        if (process.argv[i-1] == "-dump") {
            options.dump_path = process.argv[i];
        } else if (process.argv[i-1] == "-istex") {
            options.istex_path = process.argv[i];
        } else if (process.argv[i-1] == "-pmc") {
            options.pmc_path = process.argv[i];
        } else if (!process.argv[i].startsWith("-")) {
            options.action = process.argv[i];
        } 
    }

    if (!options.dump_path && !options.istex_path && !options.pmc_path) {
        console.log("dump path is not defines");
        return;
    }

    // check the input path
    var the_path;
    if (options.istex_path)
        the_path = options.istex_path;
    else if (options.pmc_path)
        the_path = options.pmc_path;
    else
        the_path = options.dump_path;
    fs.lstat(the_path, (err, stats) => {
        if (err)
            console.log(err);
        if (stats.isDirectory())
            console.log("ID dump path must be a file, not a directory");
        if (!stats.isFile()) 
            console.log("ID dump path must be a valid file");
    });

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
    processDump(options, function(ratio) {
        console.log("ratio:", ratio);
        end();
    });
}

main();
