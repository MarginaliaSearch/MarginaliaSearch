# Message Queue

Implements resilient message queueing for the application,
as well as a finite state machine library backed by the
message queue that enables long-running tasks that outlive
the execution lifespan of the involved processes. 

![Message States](msgstate.svg)

The message queue is interacted with via the Inbox and Outbox classes.

There are three types of inboxes;

Name|Description
---|---
MqSingleShotInbox|A single message is received and then the inbox is closed.
MqAsynchronousInbox|Messages are received asynchronously and can be processed in parallel.
MqSynchronousInbox|Messages are received synchronously and will be processed in order; message processing can be aborted.

A single outbox implementation exists, the `MqOutbox`, which implements multiple message sending strategies,
including blocking and asynchronous paradigms.  Lower level access to the message queue itself is provided by the `MqPersistence` class.

The inbox implementations as well as the outbox can be constructed via the `MessageQueueFactory` class. 

## Message Queue State Machine (MQSM)

The MQSM is a finite state machine that is backed by the message queue used to implement an Actor style paradigm. 

The machine itself is defined through a class that extends the 'RecordActorPrototype'; with state transitions and
names defined as implementations.

Example:

```java
class ExampleStateMachine extends RecordActorPrototype {

    public record Initial() implements ActorStep {}
    public record Greet(String message) implements ActorStep {}
    public record CountDown(int from) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) {
        return switch (self) {
            case Initial i -> new Greet("World");
            case Greet(String name) -> {
                System.out.println("Hello " + name + "!");
                yield new CountDown(5);
            }
            case CountDown (int from) -> {
                if (from > 0) {
                    System.out.println(from);
                    yield new CountDown(from - 1);
                }
                yield new End();
            }
            default -> new Error();
        };
    }
}
```

Each step should ideally be idempotent, or at least be able to handle being called multiple times.
It can not be assumed that the states are invoked within the same process, or even on the same machine,
on the same day, etc.

The usual considerations for writing deterministic Java code are advisable unless unavoidable; 
all state must be local, don't iterate over hash maps, etc.

### Create a state machine
To create an ActorStateMachine from the above class, the following code can be used:

```java
ActorStateMachine actorStateMachine = new ActorStateMachine(
        messageQueueFactory, 
        actorInboxName, 
        node,
        actorInstanceUUID,
        new ExampleStateMachine());

actorStateMachine.start();
```

The state machine will now run until it reaches the end state 
and listen to messages on the inbox for state transitions.
