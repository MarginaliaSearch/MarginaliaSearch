# Code

This is a pretty large and diverse project with many moving parts. 

You'll find a short description in each module of what it does and how it relates to other modules.
The modules each have names like "library" or "process" or "feature".  These have specific meanings. 
See [doc/module-taxonomy.md](../doc/module-taxonomy.md).

## Overview

A map of the most important components and how they relate can be found below. 

![image](../doc/diagram/conceptual-overview.svg)

The core part of the search engine is the index service, which is responsible for storing and retrieving
the document data.  The index service is partitioned and is responsible for both index lookups and spawning
per-partition processing tasks.  At least one instance of each service must be run, but more can be run
alongside.  Multiple partitions is desirable in production to distribute load across multiple physical drives, 
as well as reducing the impact of downtime.  

Search queries are delegated via the query service, which is a proxy that fans out the query to all
eligible index services.  The control service is responsible for distributing commands to the partitions
service, and for monitoring the health of the system.  It also offers a web interface for operating the system.

### Services

* [core services](services-core/) Most of these services are stateful, memory hungry, and doing heavy lifting.
    * [control](services-core/control-service)
    * [query](services-core/query-service)
       * Exposes the [functions/link-graph](functions/link-graph) subsystem
       * Exposes the [functions/search-query](functions/search-query) subsystem
    * [index](services-core/index-service)
       * Exposes the [index](index) subsystem
       * Exposes the [functions/link-graph](functions/link-graph) subsystem
       * Exposes the [execution](execution) subsystem
    * [assistant](services-core/assistant-service)
       * Exposes the [functions/math](functions/math) subsystem
       * Exposes the [functions/domain-info](functions/domain-info) subsystem
* [application services](services-application/) Mostly stateless gateways providing access to the core services.
     * [api](services-application/api-service) - public API gateway
     * [search](services-application/search-service) - marginalia search application
     * [dating](services-application/dating-service) - [https://explore.marginalia.nu/](https://explore.marginalia.nu/)
     * [explorer](services-application/explorer-service) - [https://explore2.marginalia.nu/](https://explore2.marginalia.nu/)

The system uses a service registry to find the services.  The service registry is based on zookeeper,
and is a separate service.  The registry doesn't keep track of processes, but APIs.  This means that
the system is flexible to reconfiguration.  The same code can in principle be run as a micro-service 
mesh or as a monolith.

This is an unusual architecture, but it has the benefit that you don't need to think too much about
the layout of the system.  You can just request an API and talk to it.  Because of this, several of the 
services have almost no code of their own.  They merely import a library and expose it as a service.

Services that expose HTTP endpoints tend to have more code.  They are marked with (G). 

### Processes

Processes are batch jobs that deal with data retrieval, processing and loading.  These are spawned and orchestrated by 
the index service, which is controlled by the control service.  

* [processes](processes/)
    * [crawling-process](processes/crawling-process)
    * [converting-process](processes/converting-process)
    * [loading-process](processes/loading-process)
    * [index-constructor-process](processes/index-constructor-process)

### Features

Features are relatively stand-alone components that serve some part of the domain. They aren't domain-independent,
but isolated. 

* [features-search](features-search)

### Libraries and primitives

Libraries are stand-alone code that is independent of the domain logic.  

* [common](common/) elements for creating a service, a client etc.
* [libraries](libraries/) containing non-search specific code.
    * [array](libraries/array/) - large memory mapped area library 
    * [btree](libraries/btree/) - static btree library
