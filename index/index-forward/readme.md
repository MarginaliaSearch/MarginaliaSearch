# Forward Index

The forward index contains a mapping from document id to word id. It also provides document-level
metadata, and a document-to-domain mapping. 

## Central Classes

* [ForwardIndexConverter](src/main/java/nu/marginalia/index/forward/ForwardIndexConverter.java) constructs the index.
* [ForwardIndexReader](src/main/java/nu/marginalia/index/forward/ForwardIndexReader.java) interrogates the index.