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

The MQSM is a finite state machine that is backed by the message queue.  The machine itself
is defined through a class that extends the 'AbstractStateGraph'; with state transitions and
names defined as implementations.

Example:

```java
class ExampleStateMachine extends AbstractStateGraph {
    
    @GraphState(name = "INITIAL", next="GREET")
    public void initial() {
        return "World"; // passed to the next state
    }

    @GraphState(name = "GREET", next="COUNT-TO-FIVE")
    public void greet(String name) {
        System.out.println("Hello " + name);
    }

    @GraphState(name = "COUNT-TO-FIVE", next="END")
    public void countToFive(Integer value) {
        // value is passed from the previous state, since greet didn't pass a value,
        // null will be the default.
        
        if (null == value) {
            // jumps to the current state with a value of 0
            transition("COUNT-TO-FIVE", 0);
        }


        System.out.println(++value);
        if (value < 5) {
            // Loops the current state until value = 5
            transition("COUNT-TO-FIVE", value);
        }
        
        if (value > 5) {
            // demonstrates an error condition
            error("Illegal value");
        }
        
        // Default transition is to END
    }
    
    @GraphState(name="END")
    public void end() {
        System.out.println("Done");
    }
}
```

Each method should ideally be idempotent, or at least be able to handle being called multiple times.
It can not be assumed that the states are invoked within the same process, or even on the same machine,
on the same day, etc.

The usual considerations for writing deterministic Java code are advisable unless unavoidable; 
all state must be local, don't iterate over hash maps, etc. 