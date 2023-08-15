# Control Service

The control service provides an operator's user interface, and is responsible for orchestrating the various 
processes of the system using Actors. 

Actors within the control service will spawn processes when necessary, by 
monitoring their message queue inboxes.

## Central Classes

* [ControlService](src/main/java/nu/marginalia/control/ControlService.java)
* [ControlActors](src/main/java/nu/marginalia/control/actor/ControlActors.java) - Class responsible for Actors' lifecycle
* [ProcessService](src/main/java/nu/marginalia/control/process/ProcessService.java) - Class responsible for spawning Processes

## See Also

* [processes](../../processes)
* [libraries/message-queue](../../libraries/message-queue) - The Message Queue and Actor abstractions