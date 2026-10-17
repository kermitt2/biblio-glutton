## Troubleshooting and FAQ

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

## Troubleshooting

### The matching answers 503 (Elasticsearch index issues)

It might happens that logs from Grobid show messages with error 503: 
```
INFO  [2024-08-30 12:50:17,897] org.grobid.core.utilities.Consolidation: Consolidation service returns error (503) : Service Unavailable
```
corresponding to the following error on the biblio-glutton side: 
```
35.175.72.198 - - [30/Aug/2024:12:21:13 +0000] "GET /service/lookup?parseReference=false&atitle=Latent+Dirichlet+Allocation&firstAuthor=Blei HTTP/1.1" 503 0 "-" "Apache-HttpClient/4.5.3 (Java/17.0.11)" 1710
```

This is biblio-glutton saying it cannot reach Elasticsearch, or that the index is not there. Ask
the service itself what it sees:
```
curl http://localhost:8080/service/health
```
The answer says which of the storage and Elasticsearch is the problem and why (see [Health](API.md#health)),
and the biblio-glutton log has a line starting with `ELASTICSEARCH IS NOT AVAILABLE` from the
moment it went away. The lookups by identifier keep working from the storage meanwhile:
```
curl http://localhost:8080/service/lookup?doi=10.1371/journal.pone.0265361
```
while the direct matching queries answer 503 until Elasticsearch is back:
```
curl http://localhost:8080/service/lookup?parseReference=false&atitle=Latent+Dirichlet+Allocation&firstAuthor=Blei
```

NOTE that code 404 or 400 are normal and should not be considered as an error: 404 is a record that
was not found, 400 a query without enough to search with.
