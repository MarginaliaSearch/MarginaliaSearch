# Index

These are components that offer functionality for the [index-service](../services-core/index-service).

## Indexes

There are two indexes with accompanying tools for constructing them.

* [index-forward](index-forward/) is the `document->word` index containing metadata 
about each word, such as its position. 
* [index-reverse](index-reverse/) is the `word->document` index.

These indices rely heavily on the [libraries/btree](../libraries/btree) and [libraries/btree](../libraries/array) components.
# Libraries
* [index-query](index-query/) contains structures for evaluating search queries.
* [index-journal](index-journal/) contains tools for writing and reading index data.
* [lexicon](lexicon/) contains a mapping between words' string representation and an unique integer identifier.