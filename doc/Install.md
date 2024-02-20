## The bibliographical look-up and matching REST API

Once the databases and index are built, the bibliographical REST API can be started. For building the databases and index, see the next sections below. 

### Build the service  

You need Java JDK 1.9 or more installed for building and running the tool. 

```sh
./gradlew clean build
```

### Start the server

```sh
./gradlew server
```

The service will use the default project configuration located under `biblio-glutton/config/glutton.yml`.

To check if it works, you can view a report of the data used by the service at `host:port/service/data`. For instance:

> curl localhost:8080/service/data

```json
{
    "Metadata Lookup Crossref size":"{crossref_Jsondoc=127887559}",
    "ISTEX size":"{istex_doi2ids=21325261, istex_istex2ids=21401494, istex_pii2ids=6954799}",
    "Metadata Matching Crossref size":"127970581",
    "PMID lookup size":"{pmid_doi2ids=25661624, pmid_pmc2ids=7561377, pmid_pmid2ids=33761382}",
    "DOI OA size":"{unpayWall_doiOAUrl=30635446}"
}
```

### Start optional additional GROBID service

biblio-glutton takes advantage of GROBID for parsing raw bibliographical references. This permits faster and more accurate bibliographical record matching. To use GROBID service:

* first download and install GROBID as indicated in the [documentation](https://grobid.readthedocs.io/en/latest/Install-Grobid/)

* start the service as documented [here](https://grobid.readthedocs.io/en/latest/Grobid-service/). You can change the `port` used by GROBID by updating the service config file under `grobid/grobid-home/config/grobid.yaml`  

* update if necessary the host and port information of GROBID in the biblio-glutton config file under `biblio-glutton/config/glutton.yml` (parameter `grobidPath`).

While GROBID is not required for running biblio-glutton, in particular if it is used only for bibliographical look-up, it is recommended for performing bibliographical record matching. 

