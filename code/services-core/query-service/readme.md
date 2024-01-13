The query service parses search queries and delegates work to the index service.

The [index-service](../index-service) speaks a lower level query specification language
that is difficult to build an application out of.  The query service exists as an interpreter
to that format.  

## Web Interface

The query service also offers a basic web interface for testing queries, or
running the search engine as a white-label service without all the Marginalia Search 
specific stuff.  This mode of operations is available through a `barebones` install.

The web interface also offers a JSON API for machine-based queries.

## Main Classes

* [QueryService](src/main/java/nu/marginalia/query/QueryService.java) - The REST service implementation
* [QueryGRPCService](src/main/java/nu/marginalia/query/QueryGRPCService.java) - The GRPC service implementation

## See Also

* [api/query-api](../../api/query-api)
* [features-qs/query-parser](../../features-qs/query-parser)
* [features-index/index-query](../../features-index/index-query)