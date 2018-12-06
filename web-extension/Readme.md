# biblio-glutton-web-extension

**Work in progress**

An add-on for identifying dynamically Open Access resources in the browser pages.

This extension is based on the cross-browser "WebExtension" framework.

Mozilla Firefox and Google Chrome are currently supported.

##Functionalities

This add-on performs the following task:

* Add an Open Access button next to any DOI, OpenUrl and PMID found in the browser page in case the corresponding document is available in Open Access. Clicking on the Open Access button will open a new tab with opening the corresponding PDF or web page corresponding to an freely available version of the full text. 

* citation service: for a given open OA resource, provide citation metadata in different formats (BibTeX, etc.)

* citation parsing: highlight a raw citation in the browser and get the citable metadata and full text availability and OA link if any.

## Supported identifiers and protocols

Linking work at item level (e.g. article) and will try to identifying the following identifiers in the web page:

* OpenURL 1.0, including COInS - link resolver prefixes will be examined in case of SFX and Proquest 360 Link
* DOI
* PubMed ID (PMID)
* Publisher Item Identifier (PII)

Citation formats:

* BibTeX
* TEI

## Supported browser

Currently: 

* Firefox
* Chrome

## Examples

* Example of links on a Wikipedia page: https://en.wikipedia.org/wiki/Superfluid_helium-4

## How to install

If you just want to install the extension, follow one of these steps :

  * __for Firefox__, ...
  * __for Chrome__, ...
