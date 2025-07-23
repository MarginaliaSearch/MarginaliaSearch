The execution subsystem is responsible for the execution of long running tasks on each
index node.  It lives in the [index-service](../services-core/index-service) module. 

It accomplishes this using the [message queue and actor library](../libraries/message-queue/),
which permits program state to survive crashes and reboots.

The subsystem exposes four [APIs](api/src/main/protobuf/executor-api.proto):

* Execution API - for starting and stopping tasks, also contains miscellaneous commands
* Crawl API - for managing the crawl workflow 
* Sideload API - for sideloading data 
* Export API - for exporting data