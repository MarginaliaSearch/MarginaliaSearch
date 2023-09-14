The link database contains information about links,
such as their ID, their URL, their title, their description,
and so forth.

The link database is a sqlite file.  The reason this information
is not in the MariaDB database is that this would make updates to
this information take effect in production immediately, even before
the information was searchable.

It is constructed by the [loading-process](../../processes/loading-process), and consumed 
by the [search-service](../../services-core/search-service).