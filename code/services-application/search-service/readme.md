# Search Service

This service handles search traffic and is the service
you're most directly interacting with when visiting
[marginalia-search.com](https://marginalia-search.com). 

It interprets a "human" query and translates it into a
request that gets passed into to the index service, which finds
related documents, which this service then ranks and returns
to the user.

The UI is built using [JTE templates](https://jte.gg/syntax/) and the [Jooby framework](https://jooby.io), primarily using
its MVC facilities.

When developing, it's possible to set up a mock version of the UI by running
the gradle command 

```$ ./gradlew paperDoll -i```  

The UI will be available at http://localhost:9999/, and has hot reloading of JTE classes 
and static resources.


![image](../../../doc/diagram/search-service-map.svg)

## Central classes

* [SearchService](java/nu/marginalia/search/SearchService.java) receives requests and delegates to the 
appropriate services.

* [CommandEvaluator](java/nu/marginalia/search/command/CommandEvaluator.java) interprets a user query and acts
upon it, dealing with special operations like `browse:` or `site:`.

* [SearchQueryIndexService](java/nu/marginalia/search/svc/SearchQueryIndexService.java) passes a parsed search query to the index service, and
then decorates the search results so that they can be rendered.

## See Also

* [features-search](../../features-search/)
