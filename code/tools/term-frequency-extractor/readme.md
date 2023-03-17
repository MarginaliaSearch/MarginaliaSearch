# Term Frequency Extractor

Generates a term frequency dictionary file from a batch of crawl data. 

Usage:

```shell
PATH_TO_SAMPLES=run/samples/crawl-s
export JAVA_OPTS=-Dcrawl.rootDirRewrite=/crawl:${PATH_TO_SAMPLES} 

term-frequency-extractor ${PATH_TO_SAMPLES}/plan.yaml out.dat
```

## See Also

* [libraries/term-frequency-dict](../../libraries/term-frequency-dict)