## Frequently asked questions

### How to cite this work?

If you want to cite this work, please refer to the present GitHub project, together with the [Software Heritage](https://www.softwareheritage.org/) project-level permanent identifier and do please indicate any author name. For example, with BibTeX:

```bibtex
@misc{biblio-glutton,
    title = {biblio-glutton},
    url = {https://github.com/kermitt2/biblio-glutton},
    publisher = {GitHub},
    year = {2018--2024},
    archivePrefix = {swh},
    eprint = {1:dir:a5a4585625424d7c7428654dbe863837aeda8fa7}
}
```

Note: `biblio-glutton` is spelled all lower case. 

### How can I add ISTEX identifier mapping?

If you don't know what ISTEX is, you can safely skip this section.
For creating a dump of all ISTEX identifiers associated with existing identifiers (DOI, ark, PII), use the node.js script as follow:

* install:

```sh
cd scripts
npm install requestretry
```

* Generate the json dump:

```sh
node dump-istexid-and-other-ids.js > istexIds.all
```

Be sure to have a good internet bandwidth for ensuring a high rate usage of the ISTEX REST API.

You can then move the json dump (e.g. `istexIds.all`) to the Istex data path indicated in the file `config/glutton.yaml` (by default `data/istex/`). 

### ISTEX to PubMed mapping

The mapping adds PudMed information (in particular MeSH classes) to ISTEX entries. 
See the instructions [here](pubmed-glutton/Readme.md)



