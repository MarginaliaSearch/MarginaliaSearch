# Domain Ranking

Contains domain ranking algorithms.

## Central Classes

### Algorithms
* [RankingAlgorithm](src/main/java/nu/marginalia/ranking/RankingAlgorithm.java)
* [StandardPageRank](src/main/java/nu/marginalia/ranking/StandardPageRank.java) 
* [ReversePageRank](src/main/java/nu/marginalia/ranking/ReversePageRank.java) "CheiRank"

### Data sources

* [RankingDomainFetcher](src/main/java/nu/marginalia/ranking/data/RankingDomainFetcher.java) fetches link data. 
* [RankingDomainFetcherForSimilarityData](src/main/java/nu/marginalia/ranking/data/RankingDomainFetcherForSimilarityData.java) fetches website similarity data.