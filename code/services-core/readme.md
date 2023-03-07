# Core Services

The cores services constitute the main functionality of the search engine.

* The [search-service](search-service/) parses queries, interrogates the index-service, 
 and decorates search results with metadata from the database.

* The [index-service](index-service/) contains the indexes, it answers questions about
  which documents contain which terms.

* The [assistant-service](assistant-service/) helps the search service with spelling
 suggestions other peripheral functionality.   