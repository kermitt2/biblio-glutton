# biblio-glutton

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Demo cloud.science-miner.com/glutton](https://img.shields.io/website-up-down-green-red/https/cloud.science-miner.com/glutton/service/data.svg)](http://cloud.science-miner.com/glutton/service/data)
[![SWH](https://archive.softwareheritage.org/badge/origin/https://github.com/kermitt2/biblio-glutton/)](https://archive.softwareheritage.org/browse/origin/?origin_url=https://github.com/kermitt2/biblio-glutton)

A framework dedicated to scientific bibliographic information. It includes:

- a bibliographical reference matching service: from an input such as a raw bibliographical reference and/or a combination of key metadata, the service will return the disambiguated bibliographical object with in particular its DOI and a set of metadata aggregated from CrossRef and other sources, 
- a fast metadata look-up service: from a "strong" identifier such as DOI, PMID, etc. the service will return a set of metadata aggregated from CrossRef and other sources,
- various mapping between DOI, PMID, PMC, ISTEX ID and ark, integrated in the bibliographical service,
- Open Access resolver: Integration of Open Access links via the Unpaywall dataset from Impactstory,
- Gap and daily update for CrossRef resources (via the CrossRef REST API), so that your glutton data service stays always in sync with CrossRef,
- MeSH classes mapping for PubMed articles.

biblio-glutton should be very handy if you need to run and scale a local full "Crossref" database and API, to aggregate Crossref, Pubmed and other common bibliographical records and to match a large amount of bibliographical records or raw bibliographical reference strings.

The framework is designed both for speed (with several thousands request per second for look-up) and matching accuracy. It can be [scaled](https://github.com/kermitt2/biblio-glutton#architecture) horizontally as needed and can provide high availability. 

Benchmarking against the CrossRef REST API is presented [below](https://github.com/kermitt2/biblio-glutton#matching-accuracy). 

In the Glutton family, the following complementary tools are available for taking advantage of Open Access resources: 

* [biblio-glutton-extension](https://github.com/kermitt2/biblio-glutton-extension): A browser extension (Firefox & Chrome) for providing bibliographical services, like identifying dynamically Open Access resources on web pages and providing contextual citation services.

* [biblio-glutton-harvester](https://github.com/kermitt2/biblio-glutton-harvester): A robust, fault tolerant, Python utility for harvesting efficiently (multi-threaded) a large Open Access collection of PDF (Unpaywall, PubMed Central), with the possibility to upload content on Amazon S3,

Current stable version of biblio-glutton is `0.3`. Working version is `0.4-SNAPSHOT`.

## Documentation

The full documentation is available [here](https://biblio-glutton.readthedocs.io/en/latest/).

## How to cite

If you want to cite this work, please refer to the present GitHub project, together with the [Software Heritage](https://www.softwareheritage.org/) project-level permanent identifier and do please indicate any author name. For example, with BibTeX:

```bibtex
@misc{biblio-glutton,
    title = {biblio-glutton},
    url = {https://github.com/kermitt2/biblio-glutton},
    publisher = {GitHub},
    year = {2018--2023},
    archivePrefix = {swh},
    eprint = {1:dir:a5a4585625424d7c7428654dbe863837aeda8fa7}
}
```

Note: `biblio-glutton` is spelled all lower case. 

## Main authors and contact

- Patrice Lopez ([@kermitt2](https://github.com/kermitt2), patrice.lopez@science-miner.com)

- Luca Foppiano ([@lfoppiano](https://github.com/lfoppiano))

## License

Distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 

If you contribute to this project, you agree to share your contribution following this license. 
