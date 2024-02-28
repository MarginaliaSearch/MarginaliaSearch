The link graph subsystem is responsible for knowledge about the link graph.  

A SQL database is not very well suited for this, principally it's too slow to update, 
instead the link graph is stored in memory, and atomically updated from file. 

The link graph subsystem has two components, one which injects into the partitioned services,
e.g. index or execution, and one which aggregates the results from the partitioned services,
and exposes a unified view of the link graph.