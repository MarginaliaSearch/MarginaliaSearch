## Document Database

The document database contains information about links,
such as their ID, their URL, their title, their description,
and so forth.

The document database is a sqlite file.  The reason this information
is not in the MariaDB database is that this would make updates to
this information take effect in production immediately, even before
the information was searchable.

* [DocumentLinkDbWriter](java/nu/marginalia/linkdb/docs/DocumentDbWriter.java)
* [DocumentLinkDbLoader](java/nu/marginalia/linkdb/docs/DocumentDbReader.java)

**TODO**:  This module should probably be renamed and moved into some other package. 

## See Also

The database is constructed by the [loading-process](../../processes/loading-process), and consumed by the [index-service](../../services-core/index-service).