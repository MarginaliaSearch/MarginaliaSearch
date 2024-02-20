package nu.marginalia.service.discovery;

import nu.marginalia.service.discovery.property.ApiSchema;
import nu.marginalia.service.id.ServiceId;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static nu.marginalia.service.discovery.property.ServiceEndpoint.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tag("slow")
class ZkServiceRegistryTest {
    private static final int ZOOKEEPER_PORT = 2181;
    private static final GenericContainer<?> zookeeper =
            new GenericContainer<>("zookeeper:3.8.0")
                    .withExposedPorts(ZOOKEEPER_PORT);

    List<ZkServiceRegistry> registries = new ArrayList<>();
    String connectString;
    @BeforeEach
    public void setUp() {
        zookeeper.start();
        connectString = STR."\{zookeeper.getHost()}:\{zookeeper.getMappedPort(ZOOKEEPER_PORT)}";
    }

    @AfterEach
    public void tearDown() {
        for (var registry : registries) {
            registry.shutDown();
        }
        zookeeper.stop();

        System.clearProperty("service.random-port");

    }

    ZkServiceRegistry createRegistry() {
        return new ZkServiceRegistry(CuratorFrameworkFactory.newClient(
                connectString,
                new ExponentialBackoffRetry(10, 10, 1000)));
    }

    @Test
    void getPort() {
        System.setProperty("service.random-port", "true");

        var registry1 = createRegistry();
        var registry2 = createRegistry();

        List<Integer> ports = new ArrayList<>();
        Set<Integer> portsSet = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            int port = registry1.requestPort("127.0.0.1", ApiSchema.REST, ServiceId.Search, 0);
            ports.add(port);

            // Ensure we get unique ports
            assertTrue(portsSet.add(port));
        }
        for (int i = 0; i < 50; i++) {
            int port = registry2.requestPort("127.0.0.1", ApiSchema.REST, ServiceId.Search, 0);
            ports.add(port);

            // Ensure we get unique ports
            assertTrue(portsSet.add(port));
        }
        registry1.shutDown();
        for (int i = 0; i < 500; i++) {
            // Verify we can reclaim ports
            ports.add(registry2.requestPort("127.0.0.1", ApiSchema.REST, ServiceId.Search, 0));
        }
        assertEquals(1050, ports.size());
    }

    @Test
    void getInstances() throws Exception {
        var uuid1 = UUID.randomUUID();
        var uuid2 = UUID.randomUUID();

        var registry1 = createRegistry();
        var registry2 = createRegistry();

        var endpoint1 = (RestEndpoint) registry1.registerService(ApiSchema.REST, ServiceId.Search, 0, uuid1, "127.0.0.1");
        var endpoint2 = (GrpcEndpoint) registry2.registerService(ApiSchema.GRPC, ServiceId.Search, 0, uuid2, "127.0.0.2");

        registry1.announceInstance(ServiceId.Search, 0, uuid1);
        registry2.announceInstance(ServiceId.Search, 0, uuid2);

        assertEquals(Set.of(endpoint1.asInstance(uuid1)),
                registry1.getRestEndpoints(ServiceId.Search, 0));

        assertEquals(Set.of(endpoint2.asInstance(uuid2)),
                registry1.getGrpcEndpoints(ServiceId.Search, 0));


        registry1.shutDown();
        Thread.sleep(100);

        assertEquals(Set.of(),
                registry2.getRestEndpoints(ServiceId.Search, 0));
        assertEquals(Set.of(endpoint2.asInstance(uuid2)),
                registry2.getGrpcEndpoints(ServiceId.Search, 0));
    }

    @Test
    public void announceLiveness() throws Exception {
        var registry1 = createRegistry();
        var uuid1 = UUID.randomUUID();

        assertFalse(registry1.isInstanceRunning(ServiceId.Search, 0, uuid1));
        registry1.announceInstance(ServiceId.Search, 0, uuid1);
        assertTrue(registry1.isInstanceRunning(ServiceId.Search, 0, uuid1));

        registry1.shutDown();
    }
}