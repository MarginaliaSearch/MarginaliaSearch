The query service parses search queries and delegates work to the 
index services.

The [index-service](../index-service) speaks a lower level query specification language
that is difficult to build an application out of.  The query service exists as an interpreter
to that format.  

## Web Interface

The query service also offers a basic web interface for testing queries, or
running the search engine as a white-label service without all the Marginalia Search 
specific stuff.  This mode of operations is available through a `barebones` install.

The web interface also offers a JSON API for machine-based queries.

## Central Classes

This module is almost entirely boilerplate, except the [QueryBasicInterface](src/main/java/nu/marginalia/query/QueryBasicInterface.java) 
class, which offers a REST API for querying the index.  

Much of the guts of the query service are in the [query-service](../../functions/search-query) 
module; which offers query parsing and an interface to the index service partitions.
