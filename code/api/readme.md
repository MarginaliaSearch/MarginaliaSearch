# Clients

## Core Services

* [assistant-api](assistant-api/)
* [search-api](search-api/)
* [index-api](index-api/)

These are clients for the [core services](../services-core/), along with what models
are necessary for speaking to them. They each implement the abstract client classes from
[service-client](../common/service-client). 

All that is necessary is to `@Inject` them into the constructor and then 
requests can be sent. 

**Note:** If you are looking for the public API, it's handled by the api service in [services-satellite/api-service](../services-satellite/api-service).

## MQ-API Process API

[process-mqapi](process-mqapi/) defines requests and inboxes for the message queue based API used 
for interacting with processes.   

See [common/message-queue](../common/message-queue) and [services-satellite/control-service](../services-satellite/control-service). 