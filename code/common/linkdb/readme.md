## Domain Link Database

The domain link database contains information about links
between domains.  It is a static in-memory database loaded
from a binary file.

* [DomainLinkDb](src/main/java/nu/marginalia/linkdb/DomainLinkDb.java)
* * [FileDomainLinkDb](src/main/java/nu/marginalia/linkdb/FileDomainLinkDb.java)
* * [SqlDomainLinkDb](src/main/java/nu/marginalia/linkdb/SqlDomainLinkDb.java)
* [DomainLinkDbWriter](src/main/java/nu/marginalia/linkdb/DomainLinkDbWriter.java)
* [DomainLinkDbLoader](src/main/java/nu/marginalia/linkdb/DomainLinkDbLoader.java)

## Document Database

The document database contains information about links,
such as their ID, their URL, their title, their description,
and so forth.

The document database is a sqlite file.  The reason this information
is not in the MariaDB database is that this would make updates to
this information take effect in production immediately, even before
the information was searchable.

* [DocumentLinkDbWriter](src/main/java/nu/marginalia/linkdb/DocumentDbWriter.java)
* [DocumentLinkDbLoader](src/main/java/nu/marginalia/linkdb/DocumentDbReader.java)


## See Also

These databases are constructed by the [loading-process](../../processes/loading-process), and consumed by the [index-service](../../services-core/index-service).