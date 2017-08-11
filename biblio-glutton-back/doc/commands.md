Map PMID and PMCID to ISTEX ids:

```bash
> java -Xmx4G -jar target/com.scienceminer.glutton-0.0.1.one-jar.jar -exe istexPMID -out ../data/istex/istexIds.all 
```

Launch harvesting of CORE data based on the CORE dump:

```bash
> java -Xmx4G -jar target/com.scienceminer.glutton-0.0.1.one-jar.jar -exe coreHarvesting
```

