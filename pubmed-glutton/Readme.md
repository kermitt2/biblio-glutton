## pubmed-glutton

This package can be used to parse and store the PubMed data (all MEDLINE data, with abstract, MeSH classes etc.), and provides some mapping functionalities.

Any command will first initialize the `staging area` databases, this is only done the first time a command is launched.

First build the project:

```bash

> cd pubmed-glutton

> mvn clean install

```

### MedLine

You need to download the MEDLINE/PubMed data file available at: https://www.nlm.nih.gov/databases/download/pubmed_medline.html

Then indicate the directory path where you stored these xml dumps (as warning, MEDLINE/PubMed data consist in around 900 files taking 21GB, compressed) in the file `config/glutton.yaml`: 

> pubmedDirectory: /home/lopez/data/pubmed

### ISTEX to PubMed mapping

Be sure to have the PMID and PMCID mapping to DOI, available at Euro PMC saved under your pmcDirectory path indicated in `config/glutton.yaml` (by default `data/pmc`). As of September 2018, the mapping was still available [there](ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/).

To create the ISTEX to PubMed mapping, run:

```bash
> java -Xmx1024m -jar target/com.scienceminer.glutton-0.0.1.one-jar.jar -exe istexPMID -addMeSH -out ../data/istex/istex2pmid.json
```

The resulting mapping will be written under the path introduced by the parameter `-out`.

The argument `-addMeSH` is optional, if present the mapping will include the MeSH classes.  

The mapping contains all the ISTEX identifier found in PubMed, associated with a PubMed ID, a PubMedCentral ID (if found), and - if the paramter `-addMeSH` is present, the MeSH classes corresponding to the article (descriptors with qualifiers, chemicals, and main topic information).

