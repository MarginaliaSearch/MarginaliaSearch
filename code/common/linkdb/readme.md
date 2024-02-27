## Domain Link Database

The domain link database contains information about links
between domains.  It is a static in-memory database loaded
from a binary file.

* [DomainLinkDb](java/nu/marginalia/linkdb/DomainLinkDb.java)
* * [FileDomainLinkDb](java/nu/marginalia/linkdb/FileDomainLinkDb.java)
* * [SqlDomainLinkDb](java/nu/marginalia/linkdb/SqlDomainLinkDb.java)
* [DomainLinkDbWriter](java/nu/marginalia/linkdb/DomainLinkDbWriter.java)
* [DomainLinkDbLoader](java/nu/marginalia/linkdb/DomainLinkDbLoader.java)

## Document Database

The document database contains information about links,
such as their ID, their URL, their title, their description,
and so forth.

The document database is a sqlite file.  The reason this information
is not in the MariaDB database is that this would make updates to
this information take effect in production immediately, even before
the information was searchable.

* [DocumentLinkDbWriter](java/nu/marginalia/linkdb/DocumentDbWriter.java)
* [DocumentLinkDbLoader](java/nu/marginalia/linkdb/DocumentDbReader.java)


## See Also

These databases are constructed by the [loading-process](../../processes/loading-process), and consumed by the [index-service](../../services-core/index-service).