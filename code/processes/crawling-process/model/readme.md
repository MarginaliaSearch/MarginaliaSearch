# Crawling Models

Contains crawl data models shared by the [crawling-process](../../processes/crawling-process/) and
[converting-process](../../processes/converting-process/).

To ensure backward compatibility with older versions of the data, the serialization is
abstracted away from the model classes.  

The new way of serializing the data is to use parquet files.  

The old way was to use zstd-compressed JSON.  The old way is still supported 
*for now*, but the new way is preferred as it's not only more succinct, but also 
significantly faster to read and much more portable.  The JSON support will be
removed in the future.

## Central Classes

* [CrawledDocument](java/nu/marginalia/crawling/model/CrawledDocument.java)
* [CrawledDomain](java/nu/marginalia/crawling/model/CrawledDomain.java)

### Serialization

These serialization classes automatically negotiate the serialization format based on the 
file extension.

Data is accessed through a [SerializableCrawlDataStream](java/nu/marginalia/crawling/io/SerializableCrawlDataStream.java),
which is a somewhat enhanced Iterator that can be used to read data. 

* [CrawledDomainReader](java/nu/marginalia/crawling/io/CrawledDomainReader.java)
* [CrawledDomainWriter](java/nu/marginalia/crawling/io/CrawledDomainWriter.java)

### Parquet Serialization

The parquet serialization is done using the [CrawledDocumentParquetRecordFileReader](java/nu/marginalia/crawling/parquet/CrawledDocumentParquetRecordFileReader.java)
and [CrawledDocumentParquetRecordFileWriter](java/nu/marginalia/crawling/parquet/CrawledDocumentParquetRecordFileWriter.java) classes,
which read and write parquet files respectively.

The model classes are serialized to parquet using the [CrawledDocumentParquetRecord](java/nu/marginalia/crawling/parquet/CrawledDocumentParquetRecord.java)

The record has the following fields:

* `domain` - The domain of the document
* `url` - The URL of the document
* `ip` - The IP address of the document
* `cookies` - Whether the document has cookies
* `httpStatus` - The HTTP status code of the document
* `timestamp` - The timestamp of the document
* `contentType` - The content type of the document
* `body` - The body of the document
* `etagHeader` - The ETag header of the document
* `lastModifiedHeader` - The Last-Modified header of the document

The easiest way to interact with parquet files is to use [DuckDB](https://duckdb.org/),
which lets you run SQL queries on parquet files (and almost anything else).

e.g. 
```sql
$ select httpStatus, count(*) as cnt 
       from 'my-file.parquet' 
       group by httpStatus;
┌────────────┬───────┐
│ httpStatus │  cnt  │
│   int32    │ int64 │
├────────────┼───────┤
│        200 │    43 │
│        304 │     4 │
│        500 │     1 │
└────────────┴───────┘
```