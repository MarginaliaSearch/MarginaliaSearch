# Service Client

These are base classes for all the [API](../../api) clients for talking to other [services](../service).

## Central Classes

* [AbstractDynamicClient](src/main/java/nu/marginalia/client/AbstractDynamicClient.java) base class for API clients
* [AbstractClient](src/main/java/nu/marginalia/client/AbstractClient.java) handles requests at a lower level
* [Context](src/main/java/nu/marginalia/client/Context.java) handles request tracking
* [ContextScrambler](src/main/java/nu/marginalia/client/ContextScrambler.java) handles anonymization of public IPs