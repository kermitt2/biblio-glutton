# biblio-glutton framework

The framework is based on a back-end service implementing bibliographical ingestion, aggregation and data provision functionalities and browser addon based on the WebExtension framework.  

## biblio-glutton backend

Any command will first initialize the `staging area` databases, this is only done the first time a command is launched.

First build the project:

```bash

> cd biblio-glutton-back

> mvn clean install

```

### ISTEX identifier mapping

For creating a dump of all ISTEX identifiers associated with existing identifiers (DOI, ark, pii), use the node.js script as follow:

* install:

```bash
> cd scripts
>  npm install requestretry
```

* Generate the json dump:

```bash
> node dump-istexid-and-other-ids.js > istexIds.all
```

Be sure to have a good internet bandwidth for ensuring a high rate usage of the ISTEX REST API.

You can then move the json dump (e.g. `istexIds.all`) to the Istex data path indicated in the file `config/glutton.yaml` (by default `data/istex/`). 

### ISTEX to PubMed mapping

Be sure to have the PMID and PMCID mapping to DOI, available at Euro PMC saved under your pmcDirectory path indicated in `config/glutton.yaml` (by default `data/pmc`). As of September 2018, the mapping was still available [there](ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/).

To create the ISTEX to PubMed mapping, run:

```bash
> java -Xmx1024m -jar target/com.scienceminer.glutton-0.0.1.one-jar.jar -exe istexPMID -addMeSH -out ../data/istex/istex2pmid.json
```

The resulting mapping will be written under the path introduced by the parameter `-out`.

The argument `-addMeSH` is optional, if present the mapping will include the MeSH classes.  

The mapping contains all the ISTEX identifier found in PubMed, associated with a PubMed ID, a PubMedCentral ID (if found), and - if the paramter `-addMeSH` is present, the MeSH classes corresponding to the article (descriptors with qualifiers, chemicals, and main topic information).


### Harvesting of Unpaywall dataset

See the project https://github.com/kermitt2/biblio-glutton-harvester for harvesting OA resource and for storing the uploaded PDF either on an Amazon S3 bucket or in a local storage.


### Haversting of CORE

For launching the CORE metadata harvester:

```bash
> java -Xmx1024m-jar target/com.scienceminer.glutton-0.0.1.one-jar.jar -exe coreHarvesting
```

Note that this is experimental and of very limited interest given the metadata available in the CORE dump file. 

