The executor service is a partitioned service responsible for executing and keeping
track of long running maintenance and operational tasks, such as crawling or data
processing.

It accomplishes this using the [message queue and actor library](../../libraries/message-queue/),
which permits program state to survive crashes and reboots.  The executor service is closely
linked to the [control-service](../control-service), which provides a user interface for
much of the executor's functionality.

## Central Classes

* [ExecutorActorControlService](src/main/java/nu/marginalia/actor/ExecutorActorControlService.java)

## See Also

* [api/executor-api](../../api/executor-api)