# biblio-glutton Documentation

biblio-glutton is a framework for scientific bibliographic information: a fast look-up of
bibliographical records by identifier (DOI, PMID, PMC ID, HAL ID, ISTEX ID, PII), the matching of
raw bibliographical references and partial metadata to these records, Open Access links, and
funder and research organization lookups.

## Getting Started

* [Install and run](Install.md) — build the service, start it and check what it serves

* [Elasticsearch](Elasticsearch.md) — set up the node the matching needs, with or without security

* [Docker](Docker.md) — the image, Docker Compose with Elasticsearch and Grobid, and a Hugging Face Space

* [Troubleshooting and FAQ](Frequently-asked-questions.md) — common issues and how to cite biblio-glutton

## Upgrading

* [Upgrade guide](Upgrading.md) — what to do when moving from an earlier version

## User Guide

* [Set up the storage](Set-up-storage.md) — load Crossref, the gap and daily updates, PubMed IDs, HAL, OpenAlex, ISTEX, the Funder Registry and ROR

* [REST API](API.md) — lookups by identifier, matching by metadata or raw reference, Open Access, funders and organizations, health

* [Configuration](Configuration.md) — the settings of `config/glutton.yml`

## About

* [Introduction](Introduction.md) — what biblio-glutton is and what it needs

* [How it works](Architecture.md) — architecture and runtime figures

* [License](License.md) — authors, contact and license

## Benchmarking

* [Matching accuracy](Benchmarking.md) — evaluation of the bibliographical reference matching

* [Load testing](Load-testing.md) — measuring the throughput of a running service
