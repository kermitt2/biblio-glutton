---
title: biblio-glutton
emoji: 📚
colorFrom: blue
colorTo: gray
sdk: docker
app_port: 8080
pinned: false
license: apache-2.0
short_description: Funder and research organization lookup (Crossref, ROR)
---

# biblio-glutton

[biblio-glutton](https://github.com/kermitt2/biblio-glutton) is a bibliographical look-up and
matching service. This Space runs its Docker image with what fits a Space: the
[Research Organization Registry (ROR)](https://ror.org) and the
[Crossref Open Funder Registry](https://gitlab.com/crossref/open_funder_registry), downloaded
and loaded when the Space starts (about a minute), and again every month.

- A funder by its DOI, with its identifiers in ROR:
  `/service/lookup/funder?doi=10.13039/100012950`
- An organization by its ROR identifier, by a Crossref funder DOI, or by a GRID identifier:
  `/service/lookup/ror?id=https://ror.org/02kvxyf05`,
  `/service/lookup/ror?doi=10.13039/100012950`,
  `/service/lookup/ror?grid=grid.5328.c`
- What is loaded: `/service/data`

The bibliographical records themselves (Crossref, HAL, PubMed identifiers, open access links)
and the matching of references are not in this Space: the storage takes about 175 GB and the
matching needs an Elasticsearch cluster. Lookups for them answer 404, and matching 503. See the
[documentation](https://biblio-glutton.readthedocs.io/) to run the full service, with
Docker Compose for instance.
