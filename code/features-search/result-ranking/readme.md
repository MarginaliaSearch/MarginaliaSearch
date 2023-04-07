# Result Ranking

Contains various heuristics for deciding which search results are important
with regard to a query. In broad strokes [BM-25](https://nlp.stanford.edu/IR-book/html/htmledition/okapi-bm25-a-non-binary-model-1.html)
is used, with a number of additional bonuses and penalties to rank the appropriate search
results higher.

## Central Classes

* [ResultValuator](src/main/java/nu/marginalia/ranking/ResultValuator.java)

## See Also

* [features-index/domain-ranking](../../features-index/domain-ranking) - Ranks domains