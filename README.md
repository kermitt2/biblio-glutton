# biblio-glutton framework

The framework is based on a back-end service implementing bibliographical ingestion, aggregation and data provision functionalities and browser addon based on the WebExtension framework.  

## biblio-glutton backend

Any command will first initialize the `staging area` databases, this is only done the first time a command is launched.

### ISTEX identifier mapping

For creating a dump of all ISTEX identifiers associated with existing identifiers (DOI, ark, pii), use the node.js script as f
follow:

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

To create the ISTEX to PubMed mapping, run:

```bash
> java -Xmx1024m -jar target/com.scienceminer.glutton-0.0.1.one-jar.jar -exe istexPMID -addMeSH -out ../data/istex/istex2pmid.json
```

The resulting mapping will be written under `data/istex/`

The argument `-addMeSH` is optional, if present the mapping will include the MeSH classes.  

The mapping contains all the ISTEX identifier found in PubMed, associated with a PubMed ID, a PibMedCentral ID (if found), and - if the paramter `-addMeSH` is present, the MeSH classes corresponding to the article (descriptors with qualifiers, chemicals, and main topic information).

### Haversting of CORE

For launching the CORE harvested:

```bash
> java -Xmx1024m-jar target/com.scienceminer.glutton-0.0.1.one-jar.jar -exe coreHarvesting
```

