# Crawling Models

Contains crawl data models shared by the [crawling-process](../../) and
[converting-process](../../../processes/converting-process/).

To ensure backward compatibility with older versions of the data, the serialization is
abstracted away from the model classes.  The models are serialized with the Slop library.


## Central Classes

* [CrawledDocument](java/nu/marginalia/model/crawldata/CrawledDocument.java)
* [CrawledDomain](java/nu/marginalia/model/crawldata/CrawledDomain.java)
* [SlopCrawlDataRecord](java/nu/marginalia/slop/SlopCrawlDataRecord.java)

### Serialization

These serialization classes automatically negotiate the serialization format based on the 
file extension.

Data is accessed through a [SerializableCrawlDataStream](java/nu/marginalia/io/crawldata/SerializableCrawlDataStream.java),
which is a somewhat enhanced Iterator that can be used to read data. 
