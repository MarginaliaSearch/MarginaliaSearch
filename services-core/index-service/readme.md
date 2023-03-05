# Index Service

The index service knows which document contains which keywords. 

## Central Classes

* [IndexService](src/main/java/nu/marginalia/index/IndexService.java) is the REST entry point that the internal API talks to.
* [IndexQueryService](src/main/java/nu/marginalia/index/svc/IndexQueryService.java) executes queries. 
* [SearchIndex](src/main/java/nu/marginalia/index/index/SearchIndex.java) owns the state of the index and helps with building a query strategy from parameters.
* [IndexResultValuator](src/main/java/nu/marginalia/index/results/IndexResultValuator.java) determines the best results.

## See Also

The index service relies heavily on the primitives in [index](../../index), 
such as [index-forward](../../index/index-forward/) 
and [index-reverse](../../index/index-reverse/).
