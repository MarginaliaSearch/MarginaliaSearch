package nu.marginalia.service.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.DiscoverableService;
import nu.marginalia.service.server.GrpcServer;
import nu.marginalia.test.RpcInteger;
import nu.marginalia.test.TestApiGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class GrpcSingleNodeChannelPoolTest {

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
    public void testFailsOverOnUnavailable() throws Exception {
        AtomicInteger invocations = new AtomicInteger();

        var client = createTwoServerClient(
                new FailingTestGrpcService(Status.UNAVAILABLE, invocations),
                new FailingTestGrpcService(Status.UNAVAILABLE, invocations));

        var ex = Assertions.assertThrows(StatusRuntimeException.class, () ->
                client.call(TestApiGrpc.TestApiBlockingStub::increment)
                        .run(RpcInteger.newBuilder().setValue(1).build()));

        Assertions.assertEquals(Status.Code.UNAVAILABLE, ex.getStatus().getCode());
        Assertions.assertEquals(2, invocations.get());
    }

    @Test
    public void testDoesNotFailOverOnBackpressureSignal() throws Exception {
        AtomicInteger invocations = new AtomicInteger();

        var client = createTwoServerClient(
                new FailingTestGrpcService(Status.RESOURCE_EXHAUSTED, invocations),
                new FailingTestGrpcService(Status.RESOURCE_EXHAUSTED, invocations));

        var ex = Assertions.assertThrows(StatusRuntimeException.class, () ->
                client.call(TestApiGrpc.TestApiBlockingStub::increment)
                        .run(RpcInteger.newBuilder().setValue(1).build()));

        Assertions.assertEquals(Status.Code.RESOURCE_EXHAUSTED, ex.getStatus().getCode());
        Assertions.assertEquals(1, invocations.get());
    }

    private GrpcSingleNodeChannelPool<TestApiGrpc.TestApiBlockingStub> createTwoServerClient(
            DiscoverableService service1,
            DiscoverableService service2) throws Exception
    {
        int port1 = randomHighPort();
        int port2 = port1 + 1;

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();

        createServerOnPort(port1, uuid1, service1).start();
        createServerOnPort(port2, uuid2, service2).start();

        var mockRegistry = Mockito.mock(ServiceRegistryIf.class);
        when(mockRegistry.getEndpoints(any())).thenReturn(List.of(
                new ServiceEndpoint("127.0.0.1", port1).asInstance(uuid1),
                new ServiceEndpoint("127.0.0.1", port2).asInstance(uuid2)));

        var client = new GrpcChannelPoolFactory(null, mockRegistry).createSingle(
                ServiceKey.forGrpcApi(TestApiGrpc.class, ServicePartition.any()),
                TestApiGrpc::newBlockingStub);
        clients.add(client);
        return client;
    }

    private GrpcServer createServerOnPort(int port, UUID uuid, DiscoverableService... services) throws Exception {
        var mockRegistry = Mockito.mock(ServiceRegistryIf.class);
        when(mockRegistry.requestPort(any(), any())).thenReturn(port);

        var config = new ServiceConfiguration(ServiceId.Api, 1,
                "127.0.0.1", "127.0.0.1", -1, uuid);

        var server = new GrpcServer(config, mockRegistry, List.of(services));
        servers.add(server);
        return server;
    }

    private int randomHighPort() {
        return 14000 + (int) (Math.random() * 1000);
    }

    private static class FailingTestGrpcService extends TestApiGrpc.TestApiImplBase implements DiscoverableService {
        private final Status status;
        private final AtomicInteger invocations;

        FailingTestGrpcService(Status status, AtomicInteger invocations) {
            this.status = status;
            this.invocations = invocations;
        }

        @Override
        public void increment(RpcInteger request, StreamObserver<RpcInteger> obs) {
            invocations.incrementAndGet();
            obs.onError(status.asRuntimeException());
        }
    }

}
