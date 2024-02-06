The index service is a partitioned service that knows which document contains which keywords.

![image](../../../doc/diagram/index-service-map.svg)

It is the service that most directly executes a search query.  It does this by
evaluating a low-level query, and then using the index to find the documents 
that match the query, finally ranking the results and picking the best matches.

## Central Classes

* [IndexService](src/main/java/nu/marginalia/index/IndexService.java) is the REST entry point that the internal API talks to.
* [IndexQueryService](src/main/java/nu/marginalia/index/svc/IndexQueryService.java) executes queries. 
* [SearchIndex](src/main/java/nu/marginalia/index/index/SearchIndex.java) owns the state of the index and helps with building a query strategy from parameters.
* [IndexResultValuator](src/main/java/nu/marginalia/index/results/IndexResultValuator.java) determines the best results.

## See Also

The index service relies heavily on the primitives in [features-index](../../features-index):

* [features-index/index-forward](../../features-index/index-forward/)
* [features-index/index-reverse](../../features-index/index-reverse/)
* [features-index/index-query](../../features-index/index-query)
