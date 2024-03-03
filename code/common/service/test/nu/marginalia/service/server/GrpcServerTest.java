package nu.marginalia.service.server;

import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.test.RpcInteger;
import nu.marginalia.test.TestApiGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


public class GrpcServerTest {

    List<GrpcServer> servers = new ArrayList<>();
    List<GrpcSingleNodeChannelPool<?>> clients = new ArrayList<>();

    @AfterEach
    public void close() {
        for (var server : servers) {
            try {
                server.stop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        for (var client : clients) {
            client.stop();
        }
        servers.clear();
        clients.clear();
    }

    @Test
    public void testStartStopServer() throws Exception {
        var server = createServerOnPort(randomHighPort(), UUID.randomUUID(), new TestGrpcService());

        server.start();

        server.stop();
    }

    @Test
    public void testClient() throws Exception {
        int port = randomHighPort();
        UUID serverUUID = UUID.randomUUID();

        var server = createServerOnPort(port, serverUUID, new TestGrpcService());
        server.start();

        var mockRegistry = Mockito.mock(ServiceRegistryIf.class);
        when(mockRegistry.getEndpoints(any())).thenReturn(
                Set.of(new ServiceEndpoint("127.0.0.1", port).asInstance(serverUUID)));

        var client = createClient(mockRegistry);
        client.onChange();

        var ret = client.call(TestApiGrpc.TestApiBlockingStub::increment)
                .run(RpcInteger.newBuilder().setValue(1).build());
        Assertions.assertEquals(2, ret.getValue());
    }

    @Test
    public void testClientConnSwitch() throws Exception {
        int port = randomHighPort();
        UUID serverUUID1 = UUID.randomUUID();
        UUID serverUUID2 = UUID.randomUUID();

        var server1 = createServerOnPort(port, serverUUID1, new TestGrpcService());

        server1.start();

        Set<ServiceEndpoint.InstanceAddress> endpoints = new HashSet<>();
        endpoints.add(new ServiceEndpoint("127.0.0.1", port).asInstance(serverUUID1));

        var mockRegistry = Mockito.mock(ServiceRegistryIf.class);
        when(mockRegistry.getEndpoints(any())).thenReturn(endpoints);

        var client = createClient(mockRegistry);

        var ret = client.call(TestApiGrpc.TestApiBlockingStub::increment)
                .run(RpcInteger.newBuilder().setValue(1).build());
        System.out.println(ret);

        server1.stop();
        var server2 = createServerOnPort(port + 1, serverUUID2, new TestGrpcService());
        endpoints.add(new ServiceEndpoint("127.0.0.1", port + 1).asInstance(serverUUID2));
        server2.start();
        client.onChange();

        ret = client.call(TestApiGrpc.TestApiBlockingStub::increment)
                .run(RpcInteger.newBuilder().setValue(1).build());
        System.out.println(ret);

        endpoints.clear();
        endpoints.add(new ServiceEndpoint("127.0.0.1", port + 1).asInstance(serverUUID2));
        client.onChange();

        ret = client.call(TestApiGrpc.TestApiBlockingStub::increment)
                .run(RpcInteger.newBuilder().setValue(1).build());
        System.out.println(ret);

    }

    private GrpcServer createServerOnPort(int port, UUID uuid, BindableService... services) throws Exception {
        var mockRegistry = Mockito.mock(ServiceRegistryIf.class);
        when(mockRegistry.requestPort(any(), any())).thenReturn(port);

        var config = new ServiceConfiguration(ServiceId.Api, 1,
                "127.0.0.1", "127.0.0.1", -1, uuid);

        var server = new GrpcServer(config, mockRegistry, ServicePartition.any(), List.of(services));
        servers.add(server);
        return server;
    }

    private GrpcSingleNodeChannelPool<TestApiGrpc.TestApiBlockingStub> createClient(ServiceRegistryIf mockRegistry) {
        var client = new GrpcChannelPoolFactory(null, mockRegistry).createSingle(
                ServiceKey.forGrpcApi(TestApiGrpc.class, ServicePartition.any()),
                TestApiGrpc::newBlockingStub);
        clients.add(client);
        return client;
    }

    private int randomHighPort() {
        return 12000 + (int) (Math.random() * 1000);
    }

    private static class TestGrpcService extends TestApiGrpc.TestApiImplBase {

        @Override
        public void increment(RpcInteger request, StreamObserver<RpcInteger> obs) {
            obs.onNext(RpcInteger.newBuilder().setValue(request.getValue() + 1).build());
            obs.onCompleted();
        }

        @Override
        public void count(RpcInteger request, StreamObserver<RpcInteger> obs) {
            for (int i = 0; i < request.getValue(); i++) {
                obs.onNext(RpcInteger.newBuilder().setValue(i).build());
            }
            obs.onCompleted();
        }
    }


}
