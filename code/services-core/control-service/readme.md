# Control Service

The control service provides an operator's user interface.  By default, this interface is
exposed on port 8081.  It does not offer any sort of access control or authentication.

The control service will itself execute tasks that affect the entire system, but delegate
node-specific tasks to the corresponding to the [execution subsystem](../../execution).

Conceptually the application is broken into three parts: 

* Application specific tasks relate to the high level abstractions such as blacklisting and API keys 
* System tasks relate to low level abstractions such as the message queue and event log.
* Node tasks relate to index node specific tasks, such as crawling and indexing.

## Central Classes

* [ControlService](java/nu/marginalia/control/ControlService.java)

## See Also

* [processes](../../processes)
* [libraries/message-queue](../../libraries/message-queue) - The Message Queue and Actor abstractions