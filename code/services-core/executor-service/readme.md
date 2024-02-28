The executor service is a partitioned service responsible for executing and keeping
track of long-running maintenance and operational tasks, such as crawling or data
processing.  

The executor service is closely linked to the [control-service](../control-service), 
which provides a user interface for much of the executor's functionality.

The service it itself relatively bare of code, but imports and exposes the [execution subsystem](../../execution),
which is responsible for the actual execution of tasks. 

