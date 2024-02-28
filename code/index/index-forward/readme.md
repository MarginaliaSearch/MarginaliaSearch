# Forward Index

The forward index contains a mapping from document id to various forms of document metadata.  

In practice, the forward index consists of two files, an `id` file and a `data` file.

The `id` file contains a list of sorted document ids, and the `data` file contains 
metadata for each document id, in the same order as the `id` file, with a fixed
size record containing data associated with each document id.

Each record contains a binary encoded [DocumentMetadata](../../common/model/java/nu/marginalia/model/idx/DocumentMetadata.java) object,
as well as a [HtmlFeatures](../../common/model/java/nu/marginalia/model/crawl/HtmlFeature.java) bitmask.

Unlike the reverse index, the forward index is not split into two tiers, and the data is in the same
order as it is in the source data, and the cardinality of the document IDs is assumed to fit in memory,
so it's relatively easy to construct.

## Central Classes

* [ForwardIndexConverter](java/nu/marginalia/index/forward/ForwardIndexConverter.java) constructs the index.
* [ForwardIndexReader](java/nu/marginalia/index/forward/ForwardIndexReader.java) interrogates the index.