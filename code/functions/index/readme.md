# Index

These are components that offer functionality for the [index-service](../../services-core/index-service).

## Indexes

There are two indexes with accompanying tools for constructing them.

* [index-reverse](index-reverse/) is code for `word->document` indexes. There are two such indexes, one containing only document-word pairs that are flagged as important, e.g. the word appears in the title or has a high TF-IDF. This allows good results to be discovered quickly without having to sift through ten thousand bad ones first. 

* [index-forward](index-forward/) is the `document->word` index containing metadata about each word, such as its position. It is used after identifying candidate search results via the reverse index to fetch metadata and rank the results. 

These indices rely heavily on the [libraries/btree](../../libraries/btree) and [libraries/array](../../libraries/array) components.

## Algorithms

* [domain-ranking](domain-ranking/) contains domain ranking algorithms.
* [result-ranking](result-ranking/) contains logic for ranking search results by relevance.

# Libraries

* [index-query](index-query/) contains structures for evaluating search queries.
* [index-journal](index-journal/) contains tools for writing and reading index data.

