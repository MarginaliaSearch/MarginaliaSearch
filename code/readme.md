# Code

This is a pretty large and diverse project with many moving parts. 
You'll find a short description in each module of what it does and how it relates to other modules.

## Overview

### Services
* [core services](services-core/) "macroservices", stateful, memory hungry doing heavy lifting.
* * [search](services-core/search-service)
* * [index](services-core/index-service)
* * [assistant](services-core/assistant-service)
* [sattelite services](services-satellite/) "microservices", stateless providing additional functionality.
* * [api](services-satellite/api-service)  - public API
* * [dating](services-satellite/dating-service)  - [https://explore.marginalia.nu/](https://explore.marginalia.nu/)
* * [explorer](services-satellite/explorer-service)  - [https://explore2.marginalia.nu/](https://explore2.marginalia.nu/)
* an [internal API](api/)

### Libraries and primitives
* [common](common/) elements for creating a service, a client etc.
* [index primitives](index/)
* [crawling and analysis](crawl/)
* [libraries](libraries/) containing non-search specific code.
* * [array](libraries/array/) - large memory mapped area library 
* * [btree](libraries/btree/) - static btree library
* + more
* [features](features/) containing code that is specific to search but shared among services.