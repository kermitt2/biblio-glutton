# biblio-glutton framework

Identify dynamically Open Access resources in the browser pages.

The framework is based on a back-end service implementing bibliographical ingestion, aggregation and data provision functionalities and browser addon based on the WebExtension framework.  

## biblio-glutton backend

Any command will first initialize the `staging area` databases, this is only done the first time a command is launched.

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

