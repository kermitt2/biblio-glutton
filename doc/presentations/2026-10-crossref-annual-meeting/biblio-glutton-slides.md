---
marp: true
theme: uncover
title: biblio-glutton
paginate: true
footer: biblio-glutton · Crossref annual meeting 2026
style: |
  section { font-size: 24px; padding: 48px 64px 64px; }
  section h2 { font-size: 40px; margin-bottom: 18px; }
  section h3 { font-size: 26px; font-weight: 400; opacity: 0.75; margin-top: -8px; }
  section h6 { color: #A84A14; font-size: 16px; letter-spacing: 2px; text-transform: uppercase; margin: 0 0 6px; }
  section table { font-size: 19px; margin: 0.6em auto; }
  section th, section td { text-align: left; }
  section pre { font-size: 18px; }
  section.lead h1 { font-size: 80px; }
  /* Logo row on title and closing slides: centre logos vertically, "GROBID" wordmark next to the frog */
  section.lead p:last-of-type img { vertical-align: middle; }
  section.lead p:last-of-type strong { vertical-align: middle; font-size: 1.7em; letter-spacing: 1px; color: #3D3D3D; }
  /* Captions: a blockquote ("> text") renders as a small grey note */
  section blockquote { font-size: 15px; opacity: 0.7; border: none; padding: 0; margin: 0.4em 0 0; quotes: none; }
  section blockquote::before, section blockquote::after { content: none; }

  /* Column layout: any top-level list on a slide with class "cols" becomes a grid of cards */
  section.cols > ol, section.cols > ul {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
    grid-auto-flow: column;
    gap: 1.2em;
    list-style: none;
    padding: 0;
    margin: 1em 0 0.6em;
    width: 100%;
  }
  section.cols > ol > li, section.cols > ul > li {
    border-top: 4px solid #A84A14;
    padding: 0.6em 0.2em 0;
    font-size: 0.8em;
    text-align: left;
  }
  section.cols > ol > li > strong:first-child,
  section.cols > ol > li > p:first-child > strong:first-child,
  section.cols > ul > li > strong:first-child,
  section.cols > ul > li > p:first-child > strong:first-child {
    display: block;
    font-size: 1.3em;
    margin-bottom: 0.3em;
  }
  section.cols li ul { padding-left: 1em; margin: 0.2em 0 0; }
  section.cols li li { font-size: 1em; margin-bottom: 0.2em; }
  section.invert.cols > ol > li { border-top-color: #E8915C; }
---

<!-- _class: lead -->
<!-- _paginate: false -->
<!-- _footer: '' -->

Crossref 2026 annual meeting and board election · 22 October 2026

# biblio-glutton

## Fast, local reference matching on Crossref data

Luca Foppiano · ScienciaLAB ([www.sciencialab.com](https://www.sciencialab.com)), Portugal

![h:72](slides-assets/sciencialab.png) &nbsp;&nbsp; ![h:72](https://assets.crossref.org/logo/crossref-logo-landscape-200.svg) &nbsp;&nbsp; ![h:96](slides-assets/grobid.png) **GROBID**

<!--
About 1 minute.
- In a paper, a citation is just a string: authors, title, journal, year. Often abbreviated, sometimes wrong.
- Linking papers, counting citations, finding the open access copy, building knowledge graphs: all of it starts by resolving each string to a real record with a DOI.
- biblio-glutton does that resolution locally, fast and at scale.
- Outline: where the references come from (GROBID), why biblio-glutton, what it is, what we match against, the new version, future work.
-->

---

<!-- _class: cols -->
<!-- _footer: "More: [How GROBID uses Crossref metadata to enrich bibliographic information](https://www.crossref.org/blog/how-grobid-uses-crossref-metadata-to-enrich-bibliographic-information/), Crossref blog, Oct 2026" -->

###### Where the references come from

## GROBID turns PDFs into structured data

1) **1 · Extract** PDFs are made for humans. GROBID recovers header, body and references as TEI XML.
2) **2 · Match** Extraction is strong but not perfect. Each reference is looked up in **Crossref** or **biblio-glutton**.
3) **3 · Enrich** Matched records fill gaps and fix errors: **+12% F1** on header [1].


> [1] https://grobid.readthedocs.io/en/latest/Consolidation/

<!--
About 1 minute.
- GROBID: open source machine learning library that reads scholarly PDFs and produces TEI XML.
- Extract: the header (title, authors, affiliations), the body, and the bibliography, with each reference split into fields: authors, title, journal, volume, pages, year.
- Match: parsing alone is not enough, the text can be wrong, abbreviated or garbled. So GROBID has a consolidation step: each reference, and the header, is checked against a metadata service.
- Out of the box that service is the public Crossref API. The alternative, the subject of this talk, is biblio-glutton, which you run yourself.
- Enrich: the matched record confirms the fields, completes them and adds the DOI: +12% F1 on the header (GROBID consolidation documentation).
- More detail in the Crossref blog post of 6 October (link in the footer).
-->

---

###### Why biblio-glutton?

## Citation consolidation with Grobid

- **Who needs it** Publishers, indexes, libraries, repositories, funders.
- **The real bottleneck** Large scale citation lookup: 20 request/article → 20M requests/1M articles to Crossref 
- **Requests do not all cost the same**: lookups are cheap, matches are expensive 

####  In Q1 2026, Crossref received around 100 million requests from Grobid clients

<!--
About 1 minute.
- Who needs it: anyone turning PDFs into linked metadata: publishers, indexes, libraries, repositories, funders.
- The bottleneck is the references, not the header: one header per article, but around 20 references. 1 million articles means about 20 million requests.
- Not every request costs the same:
  - lookup: "I have an identifier", a direct key read, cheap;
  - match: "I have a messy citation", a search, candidate ranking and validation, expensive.
- Scale today: in Q1 2026 Crossref received around 100 million requests from GROBID clients (Crossref blog, 6 October).
- At that volume a local service makes sense: no network round trip, no rate limit, and less load on the public API.
-->

---

<!-- _class: cols -->

###### What biblio-glutton is

## A local metadata lookup service built on Crossref

1. **100% Crossref** 
 	- Based on the Crossref public dump
 	- Identifier or metadata lookup → full Crossref record
2. **Additional features** 
	- `{PMID,PMCID,ISTEX,HAL}ID` → DOI  → lookup; 
	- Includes OpenAlex OpenAccess PDF link
	- Citation string → Grobid → lookup by "strong fields"
3. **Daily sync with crossref** with the paid Metadata Plus plan


<!--
About 1 minute.
- 100% Crossref: built from the Crossref public data dump. Give it an identifier or some metadata, get back the full Crossref record.
- How: records stored in LMDB, an embedded key-value store, so a lookup costs about a millisecond; matching goes through Elasticsearch.
- Additional features:
  - PMID, PMCID, ISTEX and HAL identifiers resolve to a DOI, then to the full record; ROR identifiers resolve to the organization.
  - Each record carries the open access PDF link from OpenAlex.
  - A raw citation string is parsed by GROBID, then matched on "strong fields": title and first author, or journal, volume and first page.
  - The best candidate is validated by string similarity; below the threshold the answer is "not found", never a wrong DOI.
- Sync: with the paid Crossref plan, daily updates keep the data current.
- Scales horizontally: more glutton servers behind a load balancer, more Elasticsearch nodes.
-->

---

###### What we match against

## Open records, from many sources

References are incomplete, abbreviated, misspelled: records must be **searchable by content**, not only reachable by identifier.

| Source | What it brings | Records |
|---|---|---|
| **Crossref** | DOI metadata: title, authors, journal, volume, pages, funders | ~170 M |
| PubMed Central | PMID ↔ PMCID ↔ DOI mapping | ~42 M |
| HAL | French open archive, curated records | 4.7 M |
| ISTEX | ISTEX identifiers and ark, with DOI and PII | 33 M |
| OpenAlex | Open access link to paper | 48 M |
| ROR · Funder Registry | Research organizations and funders | 144,717 · 45,739 |

> Counts from a fresh load in September 2026

<!--
About 1 minute.
- References are messy, so we cannot only look records up by identifier: we must search them by content (title, authors, journal). That is the difference between a key-value store and a matching service.
- Crossref is the backbone: about 170 million DOI records.
- PubMed Central: mapping between PMID, PMCID and DOI, about 42 million entries.
- HAL, the French open archive: 4.7 million curated records.
- ISTEX: the French platform for licensed publications; its identifiers and ark, linked to DOI and PII.
- OpenAlex: 48 million open access links, replacing Unpaywall.
- ROR and the Crossref Funder Registry: 144,717 organizations, 45,739 funders.
- All counts from a fresh load in September 2026.
-->

---

<!-- _class: cols -->

###### What’s next

## New version after 4 years

1. **More data**
   - Open access links from OpenAlex, replacing Unpaywall
   - ROR and the Crossref Funder Registry, refreshed monthly
   - Updated PMID / PMC mapping from the deprecated FTP
2. **Lighter**
   - One Java service: loading and indexing included, no Node.js indexer
   - Storage 60% smaller: Zstandard with a dictionary trained on Crossref
   - Dumps read straight from S3
3. **Production-ready**
   - Java 21, Elasticsearch 8 with authentication
   - Health endpoint, clear 503 instead of timeouts
   - Resumable loads, tunable matching threshold

<!--
About 1 minute.
- First new release in four years.
- More data:
  - open access links from OpenAlex, replacing the old Unpaywall dump;
  - organization and funder lookup from ROR and the Crossref Funder Registry, refreshed monthly;
  - PMID / PMCID mapping updated, replacing the deprecated FTP source.
- Lighter:
  - one Java service, loading and Elasticsearch indexing included; the previous version needed a separate Node.js indexer;
  - records compressed with Zstandard and a dictionary trained on Crossref records: about 60% less storage, no measurable cost on lookups;
  - dumps read straight from S3.
- Production-ready:
  - Java 21, Elasticsearch 8 with authentication;
  - a health endpoint, and a clear 503 when Elasticsearch is down instead of timeouts;
  - loads that resume where they stopped, daily updates that always finish, a matching threshold you can tune.
-->

---

<!-- _class: cols -->

###### Future work

## Smarter matching, evaluated again

1) **Improved matching** 
 	- Pairwise matching trained on contrastive examples, using lightweight approaches.
 	- Experiments with vector search and contextual embeddings
2) **Updated evaluation** The 2021 benchmark redone on today’s Crossref data, comparing all approaches on the same references. 
3) **Continued development** 
 	- Support future Crossref dumps
 	- Support new IDs

<!--
About 1 minute.
- Improved matching:
  - today the final decision is a single string-similarity threshold;
  - instead, a pairwise model trained on contrastive examples (correct and incorrect reference-record pairs), kept lightweight;
  - signals per candidate: title, author and journal similarity, the search score, exact match on year, volume, issue and first page;
  - vector search with contextual embeddings, to find candidates that keyword search misses: abbreviated, translated or garbled titles. Still exploratory.
- Updated evaluation:
  - the 2021 benchmark used 17,015 PubMed Central references and a Crossref index from September 2021 (back then: 2.7× faster than the Crossref API, F1 96.4 vs 95.7);
  - redo it on today's Crossref data, all approaches on the same references, reproducible.
- Continued development: support future Crossref dumps, add new lookups as needs come up.
-->

---

<!-- _class: lead -->
<!-- _paginate: false -->
<!-- _footer: '' -->

## Thank you · Questions?

Open source, Apache 2.0 · no rate limits, no data leaving your network

**Star us** on [github.com/kermitt2/biblio-glutton](https://github.com/kermitt2/biblio-glutton) 

Luca Foppiano · luca@sciencialab.com

![h:64](slides-assets/sciencialab.png) &nbsp;&nbsp; ![h:64](https://assets.crossref.org/logo/crossref-logo-landscape-200.svg) &nbsp;&nbsp; ![h:84](slides-assets/grobid.png) **GROBID**

<!--
- Open source, Apache 2.0: no rate limits, nothing leaves your network.
- Code on GitHub, a star helps; documentation at biblio-glutton.readthedocs.io.
- Thank you, happy to take questions.
-->
