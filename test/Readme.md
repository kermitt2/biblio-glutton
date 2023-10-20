
- Bibliographic data: journal title, volume and first page
```gsed -rn 's/^.*<title level="j">([^<]+)<\/title>.*<biblScope unit="volume">([^<]+)<\/biblScope>.*<biblScope unit="page">([^<]+)<\/biblScope>.*$/"\1","\2","\3"/p' *.xml > ~/Downloads/test.journalBiblio.csv```

- Bibliographic data: article title and first author
```gsed -rn 's/^.*<author>.+([A-Z^<]{3,}).+<\/author>.*<title level="a">([^<]+)<\/title>.*$/"\1","\2","\3"/p' *.xml > /Users/lfoppiano/development/scienceminer/istex/biblio-glutton/lookup/data/test/article.data.test.csv```