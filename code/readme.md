# Code

This is a pretty large and diverse project with many moving parts. 

You'll find a short description in each module of what it does and how it relates to other modules.
The modules each have names like "library" or "process" or "feature".  These have specific meanings. 
See [doc/module-taxonomy.md](../doc/module-taxonomy.md).

## Overview

A map of the most important components and how they relate can be found below. 

![image](../doc/diagram/conceptual-overview.svg)

The core part of the search engine is the index service, which is responsible for storing and retrieving
the document data.  The index serive is partitioned, along with the executor service, which is responsible for executing 
processes.  At least one instance of each service must be run, but more can be run
alongside.  Multiple partitions is desirable in production to distribute load across multiple physical drives, 
as well as reducing the impact of downtime.  

Search queries are delegated via the query service, which is a proxy that fans out the query to all
eligible index services.  The control service is responsible for distributing commands to the executor
service, and for monitoring the health of the system.  It also offers a web interface for operating the system.

### Services
* [core services](services-core/) Most of these services are stateful, memory hungry, and doing heavy lifting.
* * [control](services-core/control-service)
* * [query](services-core/query-service)
* * [index](services-core/index-service)
* * [executor](services-core/executor-service)
* * [assistant](services-core/assistant-service)
* [application services](services-application/) Mostly stateless gateways providing access to the core services.
* * [api](services-application/api-service)  - public API
* * [search](services-application/search-service) - marginalia search application
* * [dating](services-application/dating-service)  - [https://explore.marginalia.nu/](https://explore.marginalia.nu/)
* * [explorer](services-application/explorer-service)  - [https://explore2.marginalia.nu/](https://explore2.marginalia.nu/)
* an [internal API](api/)

### Processes

Processes are batch jobs that deal with data retrieval, processing and loading.  These are spawned and orchestrated by 
the executor service, which is controlled by the control service.  

* [processes](processes/)
* * [crawling-process](processes/crawling-process)
* * [converting-process](processes/converting-process)
* * [loading-process](processes/loading-process)

#### Tools

* * [term-frequency-extractor](tools/term-frequency-extractor)

### Features

Features are relatively stand-alone components that serve some part of the domain. They aren't domain-independent,
but isolated. 

* [features-search](features-search)
* [features-crawl](features-crawl)
* [features-convert](features-convert)
* [features-index](features-index)

### Libraries and primitives

Libraries are stand-alone code that is independent of the domain logic.  

* [common](common/) elements for creating a service, a client etc.
* [libraries](libraries/) containing non-search specific code.
* * [array](libraries/array/) - large memory mapped area library 
* * [btree](libraries/btree/) - static btree library
