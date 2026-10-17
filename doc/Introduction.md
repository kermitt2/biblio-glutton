# biblio-glutton

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![SWH](https://archive.softwareheritage.org/badge/origin/https://github.com/kermitt2/biblio-glutton/)](https://archive.softwareheritage.org/browse/origin/?origin_url=https://github.com/kermitt2/biblio-glutton)

A framework dedicated to scientific and technical bibliographic information. It includes:

- a bibliographical reference matching service: from an input such as a raw bibliographical reference and/or a combination of key metadata, the service will return the disambiguated bibliographical object with in particular its DOI and a set of metadata aggregated from Crossref and other sources,
- a fast metadata look-up service: from a "strong" identifier such as DOI, PMID, PMC ID, HAL ID, ISTEX ID or PII, the service will return a set of metadata aggregated from Crossref and other sources,
- various mapping between DOI, PMID, PMC ID, HAL ID, ISTEX ID, PII and ark, integrated in the bibliographical service,
- HAL archive records, next to the Crossref ones,
- funder and research organization lookup, from the Crossref Open Funder Registry and ROR (Research Organization Registry), with the bridge from a funder DOI to its organization in ROR,
- Open Access resolver: Integration of Open Access links via the OpenAlex dataset,
- Gap and daily update for Crossref resources (via the Crossref REST API), so that your glutton data service stays always in sync with Crossref.

biblio-glutton should be very handy if you need to run and scale a local full "Crossref" database and API, to aggregate Crossref, PubMed, HAL and other common bibliographical records and to match a large amount of bibliographical records or raw bibliographical reference strings.

The framework is designed both for speed (with several thousands request per second for look-up) and matching accuracy. It can be [scaled](Architecture.md) horizontally as needed and can provide high availability. 

Benchmarking against the Crossref REST API is presented in [Matching accuracy](Benchmarking.md). 

In the Glutton family, the following complementary tools are available for taking advantage of Open Access resources: 

* [biblio-glutton-extension](https://github.com/kermitt2/biblio-glutton-extension): A browser extension (Firefox & Chrome) for providing bibliographical services, like identifying dynamically Open Access resources on web pages and providing contextual citation services.

* [biblio-glutton-harvester](https://github.com/kermitt2/biblio-glutton-harvester): A robust, fault tolerant, Python utility for harvesting efficiently (multi-threaded) a large Open Access collection of PDF (Unpaywall, PubMed Central), with the possibility to upload content on Amazon S3.

The current version of biblio-glutton is `0.4.0`.

## Requirements

biblio-glutton is a Java software requiring JDK 21.

You will need an Elasticsearch cluster to make possible bibliographical reference matching and resolution; the lookups by identifier work without it. The current version is tested with Elasticsearch 8 (8.19), see [Elasticsearch](Elasticsearch.md) for running one with Docker. OpenSearch is not supported.

Optionally a GROBID service can be added to parse the raw bibliographical reference strings before their matching. Use the latest GROBID version (0.9.1 at the time of writing).

For storing the compiled metadata records, you will need a large amount of SSD space: around 300 GB for all the resources, less with the record compression of 0.4.0 (see [Set up the storage](Set-up-storage.md)). SSD ensures fast and predictable runtime lookup based on memory paging. 
