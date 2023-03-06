# Core Service Clients

These are clients for the [core services](../services-core/), along with what models
are necessary for speaking to them. They each implement the abstract client classes from
[service-client](../common/service-client). 

All that is necessary is to `@Inject` them into the constructor and then 
requests can be sent. 