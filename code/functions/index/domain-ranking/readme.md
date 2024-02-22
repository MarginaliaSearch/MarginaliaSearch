# Domain Ranking

Contains domain ranking algorithms.  The domain ranking algorithms are based on 
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

* [PageRankDomainRanker](src/main/java/nu/marginalia/ranking/PageRankDomainRanker.java) - Ranks domains using the 
 PageRank or Personalized PageRank algorithm depending on whether a list of influence domains is provided.

### Data sources

* [LinkGraphSource](src/main/java/nu/marginalia/ranking/data/LinkGraphSource.java) - fetches the link graph
* [InvertedLinkGraphSource](src/main/java/nu/marginalia/ranking/data/InvertedLinkGraphSource.java) - fetches the inverted link graph
* [SimilarityGraphSource](src/main/java/nu/marginalia/ranking/data/SimilarityGraphSource.java) - fetches the similarity graph from the database 

Note that the similarity graph needs to be precomputed and stored in the database for
the similarity graph source to be available.

## See Also

* [result-ranking](../result-ranking) - Ranks search results

## Useful Resources

* [The PageRank Citation Ranking: Bringing Order to the Web](http://ilpubs.stanford.edu:8090/422/1/1999-66.pdf)
