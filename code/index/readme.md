# Index

This index subsystem contains the components that make up the search index.

It exposes an API for querying the index, and contains the logic 
for ranking search results.  It does not parse the query, that is
the responsibility of the [search-query](../functions/search-query) module.

The central class of the index subsystem is the [IndexGrpcService](java/nu/marginalia/index/IndexGrpcService.java) class,
which is a gRPC service that exposes the index to the rest of the system.

## Indexes

There are two indexes with accompanying tools for constructing them.

* [index-reverse](index-reverse/) is code for `word->document` indexes. There are two such indexes, one containing only document-word pairs that are flagged as important, e.g. the word appears in the title or has a high TF-IDF. This allows good results to be discovered quickly without having to sift through ten thousand bad ones first. 

* [index-forward](index-forward/) is the `document->word` index containing metadata about each word, such as its position. It is used after identifying candidate search results via the reverse index to fetch metadata and rank the results. 

Additionally, the [index-journal](index-journal/) contains code for constructing a journal of the index, which is used to keep the index up to date.

These indices rely heavily on the [libraries/btree](../libraries/btree) and [libraries/array](../libraries/array) components.

---

# Result Ranking

The module is also responsible for ranking search results, and contains various heuristics
for deciding which search results are important with regard to a query. In broad strokes [BM-25](https://nlp.stanford.edu/IR-book/html/htmledition/okapi-bm25-a-non-binary-model-1.html)
is used, with a number of additional bonuses and penalties to rank the appropriate search
results higher.

## Central Classes

* [ResultValuator](java/nu/marginalia/ranking/results/ResultValuator.java)

---

# Domain Ranking

The module contains domain ranking algorithms.  The domain ranking algorithms are based on
the JGraphT library.

Two principal algorithms are available, the standard PageRank algorithm,
and personalized pagerank; each are available for two graphs, the link graph
and a similarity graph where each edge corresponds to the similarity between
the sets of incident links to two domains, their cosine similarity acting as
the weight of the links.

With the standard PageRank algorithm, the similarity graph does not produce
anything useful, but something magical happens when you apply Personalized PageRank
to this graph.  It turns into a very good "vibe"-sensitive ranking algorithm.

It's unclear if this is a well known result, but it's a very interesting one
for creating a ranking algorithm that is focused on a particular segment of the web.

## Central Classes

* [PageRankDomainRanker](java/nu/marginalia/ranking/domains/PageRankDomainRanker.java) - Ranks domains using the
  PageRank or Personalized PageRank algorithm depending on whether a list of influence domains is provided.

### Data sources

* [LinkGraphSource](java/nu/marginalia/ranking/domains/data/LinkGraphSource.java) - fetches the link graph
* [InvertedLinkGraphSource](java/nu/marginalia/ranking/domains/data/InvertedLinkGraphSource.java) - fetches the inverted link graph
* [SimilarityGraphSource](java/nu/marginalia/ranking/domains/data/SimilarityGraphSource.java) - fetches the similarity graph from the database

Note that the similarity graph needs to be precomputed and stored in the database for
the similarity graph source to be available.

## Useful Resources

* [The PageRank Citation Ranking: Bringing Order to the Web](http://ilpubs.stanford.edu:8090/422/1/1999-66.pdf)
