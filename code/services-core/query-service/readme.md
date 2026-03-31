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

### /search

Returns HTML by default, or JSON when `Accept: application/json` is set.

Parameters:
- `query` or `q` (required) - search query string
- `count` (default 20, max 100) - number of results
- `dc` (default 2, 1-100) - max results per domain
- `timeout` (default 150, 50-250) - query timeout in milliseconds
- `filter` (optional) - named system filter (e.g. POPULAR, SMALLWEB, BLOGOSPHERE, NO_FILTER, VINTAGE, TILDE, ACADEMIA, PLAIN_TEXT, FOOD, FORUM, WIKI, DOCS)
- `nsfw` (default 1) - NSFW filter tier: 0=off, 1=danger, 2=porn_and_gambling
- `page` (default 1) - page number
- `lang` (default "en") - language ISO code

### /qdebug

Query debugging endpoint for ranking parameter tuning. 

## Central Classes

[QueryWebApi](java/nu/marginalia/query/QueryWebApi.java) handles the
`/search` endpoint.

[QueryDebugInterface](java/nu/marginalia/query/QueryDebugInterface.java) handles the
`/qdebug` endpoint.

Much of the guts of the query service are in the [search-query](../../functions/search-query) 
module, which offers query parsing and an interface to the index service partitions.
