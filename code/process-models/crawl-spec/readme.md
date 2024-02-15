# Crawl Spec

A crawl spec is a list of domains to be crawled.  It is a parquet file with the following columns:

- `domain`: The domain to be crawled
- `crawlDepth`: The depth to which the domain should be crawled
- `urls`: A list of known URLs to be crawled

Crawl specs are used to define the scope of a crawl in the absence of known domains.

The [CrawlSpecRecord](src/main/java/nu/marginalia/model/crawlspec/CrawlSpecRecord.java) class is 
used to represent a record in the crawl spec.  

The [CrawlSpecRecordParquetFileReader](src/main/java/nu/marginalia/io/crawlspec/CrawlSpecRecordParquetFileReader.java)
and [CrawlSpecRecordParquetFileWriter](src/main/java/nu/marginalia/io/crawlspec/CrawlSpecRecordParquetFileWriter.java)
classes are used to read and write the crawl spec parquet files.
