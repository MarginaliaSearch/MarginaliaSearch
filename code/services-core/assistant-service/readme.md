# Assistant Service

The assistant service helps the search service by offering various peripheral functionality, such as spelling correction.

The assistant service exposes the [functions/domain-info](../../functions/domain-info) subsystem,
which is responsible for knowledge about domains; and the [functions/math](../../functions/math) subsystem,
which is responsible for evaluating mathematical operations, spelling correction, and other peripheral 
functionality.

## Central Classes

* [AssistantService](java/nu/marginalia/assistant/AssistantService.java) handles REST requests and delegates to the appropriate handling classes. 