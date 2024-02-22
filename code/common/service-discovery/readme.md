# Service Discovery

Contains classes for helping services discover each other,
and managing connections between them.

## Service Registry

The service registry is a class that keeps track of the services
that are currently running, and their connection information.

The service register implementation is based on [Zookeeper](https://zookeeper.apache.org/),
which is a distributed coordination service.  This lets services register
themselves and announce their liveness, and then discover each other.

It supports multiple instances of a service running, and
supports running the system bare-metal, where it will assign
ports to the services from a range.

* REST services are registered on a per-node basis, and are always non-partitioned.
* gRPC services are registered on a per-api basis, and can be partitioned
  or non-partitioned.  This means that if a gRPC api is moved between nodes,
  the clients will not need to be reconfigured.

To be discoverable, the caller must first register their
services:

```java
// Register one or more services
serviceRegistry.registerService(
        ServiceKey.forRest(serviceId, nodeId),
instanceUuid, // unique
externalAddress); // bind-address

// Non-partitioned GRPC service
        serviceRegistry.registerService(
        ServiceKey.forServiceDescriptor(descriptor, ServicePartition.any()),
instanceUuid,
externalAddress);

// Partitioned GRPC service
        serviceRegistry.registerService(
        ServiceKey.forServiceDescriptor(descriptor, ServicePartition.partition(5)),
instanceUuid,
externalAddress);

// (+ any other services)
```

Then, the caller must announce their instance.  Before this is done,
the service is not discoverable.

```java
registry.announceInstance(instanceUUID);
```

All of this is done automatically by the `Service` base class
in the [service](../service/) module.

To discover a service, the caller can query the registry:

```java
Set<InstanceAddress> endpoints = registry.getEndpoints(serviceKey);
```

It's also possible to subscribe to changes in the registry, so that
the caller can be notified when a service comes or goes, with `registry.registerMonitor()`.

However the `GrpcChannelPoolFactory` is a more convenient way to access the services,
it will let the caller create a pool of channels to the services, and manage their
lifecycle, listen to lifecycle notifications and so on.

## gRPC Channel Pool

From the [GrpcChannelPoolFactory](src/main/java/nu/marginalia/service/client/GrpcChannelPoolFactory.java), two types of channel pools can be created
that are aware of the service registry:

* [GrpcMultiNodeChannelPool](src/main/java/nu/marginalia/service/client/GrpcMultiNodeChannelPool.java) - This pool permits 1-n style communication with partitioned services
* [GrpcSingleNodeChannelPool](src/main/java/nu/marginalia/service/client/GrpcSingleNodeChannelPool.java) - This pool permits 1-1 style communication with non-partitioned services.
  if multiple instances are running, it will use one of them and fall back
  to another if the first is not available.

The pools can generate calls to the gRPC services, and will manage the lifecycle of the channels.

The API is designed to be simple to use, and will permit the caller to access the Stub interfaces
for the services through a fluent API.

### Example Usage of the GrpcSingleNodeChannelPool

```java
// create a pool for a non-partitioned service
channelPool = factory.createSingle(
        ServiceKey.forGrpcApi(MathApiGrpc.class, ServicePartition.any()),
                        MathApiGrpc::newBlockingStub);

// blocking call
Response response = channelPool
        .call(MathApiGrpc.MathApiBlockingStub::dictionaryLookup)
        .run(request);

// sequential blocking calls
List<Response> response = channelPool
        .call(MathApiGrpc.MathApiBlockingStub::dictionaryLookup)
        .runFor(request1, request2);


// async call
Future<Response> response = channelPool
        .call(MathApiGrpc.MathApiBlockingStub::dictionaryLookup)
        .async(myExecutor)
        .run(request);

// multiple async calls
Future<List<Response>> response = channelPool
        .call(MathApiGrpc.MathApiBlockingStub::dictionaryLookup)
        .async(myExecutor)
        .runFor(request1, request2);
```

### Example Usage of the GrpcSingleNodeChannelPool

```java
// create a pool for a partitioned service
channelPool = factory.createMulti(
        ServiceKey.forGrpcApi(MathApiGrpc.class, ServicePartition.multi()),
                        MathApiGrpc::newBlockingStub);

// blocking call
List<Response> response = channelPool
        .call(MathApiGrpc.MathApiBlockingStub::dictionaryLookup)
        .run(request);

// async call
Future<List<Response>> response = channelPool
        .call(MathApiGrpc.MathApiBlockingStub::dictionaryLookup)
        .async(myExecutor)
        .runEach(request);

// async call, will fail or succeed as a group 
Future<List<Response>> response = channelPool
        .call(MathApiGrpc.MathApiBlockingStub::dictionaryLookup)
        .async(myExecutor)
        .runAll(request1, request2);
```


### Central Classes

* [ServiceRegistryIf](src/main/java/nu/marginalia/service/discovery/ServiceRegistryIf.java)
* [ZkServiceRegistry](src/main/java/nu/marginalia/service/discovery/ZkServiceRegistry.java)