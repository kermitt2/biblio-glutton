## pubmed-glutton

This package can be used to parse and store the PubMed data (all MEDLINE data, with abstract, MeSH classes etc.), and provides some mapping functionalities.

Any command will first initialize the `staging area` databases, this is only done the first time a command is launched.

First build the project:

```bash

> cd pubmed-glutton

> ./gradlew clean build

```

### MEDLINE/PubMed data

You need to download the MEDLINE/PubMed data file available at: https://www.nlm.nih.gov/databases/download/pubmed_medline.html

Then indicate the directory path where you stored these compressed xml dumps (as warning, MEDLINE/PubMed data consist in around 975 files taking 26GB, compressed) in the global configuration file `biblio-glutton/config/glutton.yml`: 

> pubmedDirectory: /home/lopez/data/pubmed


### MEDLINE/PubMed export in the Crossref JSON format

Convert the downloaded MEDLINE/PubMed metadata XML files into a dump in the Crossref JSON format. 

```bash
> java -Xmx1024m -jar build/libs/com.scienceminer.glutton-0.2-SNAPSHOT-onejar.jar -exe pubmedDump -tdata /path/to/medline/ -out /path/to/new/dump/
```

### MEDLINE/PubMed indexing

Be sure to have the full Medline data downloaded (see https://www.nlm.nih.gov/databases/download/pubmed_medline.html), and indicate in the config file `biblio-glutton/config/glutton.yml` the path to the repository containing the Medline bulk download, e.g. 

```yaml
pubmedDirectory: /mnt/data/biblio/medline2020
```

At first launch, the data will be parsed and stored in the LMDB database for fast access and manipulation. This is only done one time and takes a few hours (around 3 hours) with SSD.   

Be also sure to have the PMID and PMCID mapping to DOI, available at Euro PMC saved under your pmcDirectory path indicated in `biblio-glutton/config/glutton.yml` (by default `data/pmc`). As of September 2018, the mapping was still available [there](ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/). Medline records will then be aggregated with DOI identifiers. 

To index the PubMed data, run:

```bash
> java -Xmx1024m -jar build/libs/com.scienceminer.glutton-0.2-SNAPSHOT-onejar.jar -exe pubmed 
```

### MEDLINE/PubMed/PubMed Central Export 

After the launch of the above extraction and indexing, it is possible to export the PubMed records according to a selection of MeSH class descriptors. 

To export a sub-corpus of PubMed records, run:

```bash
> java -Xmx1024m -jar build/libs/com.scienceminer.glutton-0.2-SNAPSHOT-onejar.jar -exe pubmedExport -tdata src/test/resources/classes-mesh.csv -out ~/tmp/mesh/
```

In case you are only interested in PubMed Central entries (e.g. for exploiting full text with text mining), use `pmcExport` as `-exe` action: 

```bash
> java -Xmx1024m -jar build/libs/com.scienceminer.glutton-0.2-SNAPSHOT-onejar.jar -exe pmcExport -tdata src/test/resources/classes-mesh.csv -out ~/tmp/mesh/
```

### ISTEX to PubMed mapping

Be sure to have the PMID and PMCID mapping to DOI, available at Euro PMC saved under your pmcDirectory path indicated in `biblio-glutton/config/glutton.yml` (by default `data/pmc`). As of September 2018, the mapping was still available [there](ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/).

To create the ISTEX to PubMed mapping, run:

```bash
> java -Xmx1024m -jar build/libs/com.scienceminer.glutton-0.2-SNAPSHOT-onejar.jar -exe istexPMID -addMeSH -out ../data/istex/istex2pmid.json
```

The resulting mapping will be written under the path introduced by the parameter `-out`.

The argument `-addMeSH` is optional, if present the mapping will include the MeSH classes.  

The mapping contains all the ISTEX identifier found in PubMed, associated with a PubMed ID, a PubMedCentral ID (if found), and - if the paramter `-addMeSH` is present, the MeSH classes corresponding to the article (descriptors with qualifiers, chemicals, and main topic information).

