## Matching accuracy

Here is some evaluation on the bibliographical reference matching.

### Dataset

We created a dataset of [17,015 bibliographical reference/DOI pairs](doc/references-doi-matching.json.gz) with GROBID and the PMC 1943 sample (a set of 1943 PubMed Central articles from 1943 different journals with both PDF and XML NLM files available, see below). For the bibliographical references present in the NLM file with a DOI, we try to align the raw reference string extracted from the PDF by GROBID and the parsed XML present in the NLM file. Raw reference string are thus coming from the PDF, and we included additional metadata as extracted by GROBID from the PDF. 

Example of the two first of the 17.015 entries: 

```json
{"reference": "Classen M, Demling L. Endoskopishe shinkterotomie der papilla \nVateri und Stein extraction aus dem Duktus Choledochus [Ger-\nman]. Dtsch Med Wochenschr. 1974;99:496-7.", "doi": "10.1055/s-0028-1107790", "pmid": "4835515", "atitle": "Endoskopishe shinkterotomie der papilla Vateri und Stein extraction aus dem Duktus Choledochus [German]", "firstAuthor": "Classen", "jtitle": "Dtsch Med Wochenschr", "volume": "99", "firstPage": "496"},
{"reference": "Kawai K, Akasaka Y, Murakami K. Endoscopic sphincterotomy \nof the ampulla of Vater. Gastrointest Endosc. 1974;20:148-51.", "doi": "10.1016/S0016-5107(74)73914-1", "pmid": "4825160", "atitle": "Endoscopic sphincterotomy of the ampulla of Vater", "firstAuthor": "Kawai", "jtitle": "Gastrointest Endosc", "volume": "20", "firstPage": "148"},
```

The goal of Glutton matching is to identify the right DOI from raw metadata. We compare the results with the CrossRef REST API, using the `query.bibliographic` field for raw reference string matching, and author/title field queries for first author lastname (`query.author`) plus title matching (`query.title`). 

Limits: 

- The DOI present in the NLM files are not always reliable (e.g. DOI not valid anymore following some updates in CrossRef). A large amount of the matching errors are actually not due to the matching service, but to NLM reference DOI data quality. However, errors will be the same for all matching services, so it's still valid for comparing them, although for this reason the resulting accuracy is clearly lower than what it should be.

- GROBID extraction is not always reliable, as well the alignment mechanism with NLM (based on soft match), and some raw reference string might not be complete or include unexpected extra material from the PDF. However, this can be view as part of the matching challenge in real world conditions! 

- the NLM references with DOI are usually simpler reference than in general: there are much fewer abbreviated references (without title nor authors) and references without titles as compared to general publications from non-medicine publishers. 



### How to run the evaluation

You can use the DOI matching evaluation set (with 17,015 bibliographical reference/DOI pairs) from the indicated above address ([here](doc/references-doi-matching.json.gz)) or recreate this dataset with GROBID as follow:

- [Install GROBID](https://grobid.readthedocs.io/en/latest/Install-Grobid/).

- Download the [PMC 1943 sample](https://grobid.s3.amazonaws.com/PMC_sample_1943.zip) (around 1.5GB in size).

- Create the evaluation dataset:

> ./gradlew PrepareDOIMatching -Pp2t=ABS_PATH_TO_PMC/PMC_sample_1943 
    
The evaluation dataset will be saved under `ABS_PATH_TO_PMC/PMC_sample_1943` with the name `references-doi-matching.json`

- For launching an evaluation: 

1) Select the matching method (`crossref` or `glutton`) in the `grobid-home/config/grobid.yaml` file: 

```yaml
consolidation:
    # define the bibliographical data consolidation service to be used, either "crossref" for CrossRef REST API or 
    # "glutton" for https://github.com/kermitt2/biblio-glutton
    #service: "crossref"
    service: "glutton"
```

2) If Glutton is setected, start the Glutton server as indicated above (we assume that it is running at `localhost:8080`).

3) Launch from GROBID the evaluation, indicating the path where the evaluation dataset has been created - here we suppose that the file `references-doi-matching.json` has been saved under `ABS_PATH_TO_PMC/PMC_sample_1943`: 

> ./gradlew EvaluateDOIMatching -Pp2t=ABS_PATH_TO_PMC/PMC_sample_1943


### Full raw bibliographical reference matching

Runtime corresponds to a processing on a single machine running Glutton REST API server, ElasticSearch and GROBID evaluation with CRF for the citation model, with CrossRef index dated Sept. 2021. 

```
======= GLUTTON API ======= 
17015 bibliographical references processed in 1145.593 seconds, 0.06732841610343815 seconds per bibliographical reference.
Found 16699 DOI

precision:      97.33
recall:         95.52
F1-score:       96.42
```

With `BiLSTM-CRF` model instead of CRF for parsing the raw references prior to matching:

```
======= GLUTTON API ======= 

precision:      97.34
recall:         95.83
f-score:        96.58
```

In the case of CrossRef API, we use as much as possible the concurrent queries (usually 50) allowed by the service with the GROBID CrossRef multithreaded client. 

```
======= CROSSREF API ======= 

17015 bibliographical references processed in 3057.464 seconds, 0.1797 seconds per bibliographical reference.
Found 16502 DOI

======= CROSSREF API ======= 

precision:      97.19
recall:         94.26
F1-score:       95.69
```
