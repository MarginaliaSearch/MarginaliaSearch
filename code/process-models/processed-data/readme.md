The processed-data package contains models and logic for
reading and writing parquet files with the output from the
[converting-process](../../processes/converting-process).

Main models:

* [DocumentRecord](src/main/java/nu/marginalia/model/processed/DocumentRecord.java)
* * [DocumentRecordKeywordsProjection](src/main/java/nu/marginalia/model/processed/DocumentRecordKeywordsProjection.java)
* * [DocumentRecordMetadataProjection](src/main/java/nu/marginalia/model/processed/DocumentRecordMetadataProjection.java)
* [DomainLinkRecord](src/main/java/nu/marginalia/model/processed/DomainLinkRecord.java)
* [DomainRecord](src/main/java/nu/marginalia/model/processed/DomainRecord.java)

Since parquet is a column based format, some of the readable models are projections
that only read parts of the input file.

## See Also

[third-party/parquet-floor](../../../third-party/parquet-floor)