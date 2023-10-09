The query service parses search queries and delegates work to the index service.

The [index-service](../index-service) speaks a lower level query specification language
that is difficult to build an application out of.  The query service exists as an interpreter
to that format.

## Main Classes

* [QueryService](src/main/java/nu/marginalia/query/QueryService.java)

## See Also

* [api/query-api](../../api/query-api)
* [features-qs/query-parser](../../features-qs/query-parser)
* [features-index/index-query](../../features-index/index-query)