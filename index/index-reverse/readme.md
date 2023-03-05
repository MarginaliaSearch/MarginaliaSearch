# Reverse Index

The reverse index contains a mapping from word to document id. It also provides access to
term-level metadata.

## Central Classes

* [ReverseIndexConverter](src/main/java/nu/marginalia/index/reverse/ReverseIndexConverter.java) constructs the index.
* [ReverseIndexReader](src/main/java/nu/marginalia/index/reverse/ReverseIndexReader.java) interrogates the index.