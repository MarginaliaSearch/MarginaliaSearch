# Reverse Index

The reverse index contains a mapping from word to document id. 

There are two tiers of this index, one priority index which only indexes terms that are flagged with priority flags<sup>1</sup>,
and a full index that indexes all terms. The full index also provides access to term-level metadata, while the priority
index is a binary index.

[1] See WordFlags in [common/model](../../common/model/) and
KeywordMetadata in [features-convert/keyword-extraction](../../features-convert/keyword-extraction).

## Central Classes

* [ReverseIndexFullConverter](src/main/java/nu/marginalia/index/full/ReverseIndexFullConverter.java) constructs the full index.
* [ReverseIndexFullReader](src/main/java/nu/marginalia/index/full/ReverseIndexFullReader.java) interrogates the full index.
* [ReverseIndexPriorityConverter](src/main/java/nu/marginalia/index/priority/ReverseIndexPriorityConverter.java) constructs the priority index.
* [ReverseIndexPriorityReader](src/main/java/nu/marginalia/index/priority/ReverseIndexPriorityReader.java) interrogates the priority index.