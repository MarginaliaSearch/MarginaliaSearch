The link graph partition module is responsible for knowledge about the link graph
for a single index node.  It's based on in-memory data structures, and is updated
atomically from file.

## Central Classes

* [PartitionLinkGraphService](java/nu/marginalia/linkgraph/PartitionLinkGraphService.java)
* [DomainLink](java/nu/marginalia/linkgraph/DomainLinks.java)
* * [FileDomainLinks](java/nu/marginalia/linkgraph/impl/FileDomainLinks.java)
* [DomainLinksWriter](java/nu/marginalia/linkgraph/io/DomainLinksWriter.java)
* [DomainLinksLoader](java/nu/marginalia/linkgraph/io/DomainLinksLoader.java)