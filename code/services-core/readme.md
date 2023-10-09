# Core Services

The cores services constitute the main functionality of the search engine,
*relatively* agnostic to the Marginalia application.

* The [index-service](index-service/) contains the indexes, it answers questions about
  which documents contain which terms.

* The [query-service](query-service/) Interprets queries and delegates work to index-service.
 
* The [control-service](control-service/) provides an operator's user interface, and is responsible
  for orchestrating the various processes of the system. 

* The [assistant-service](assistant-service/) helps the search service with spelling
 suggestions other peripheral functionality.   