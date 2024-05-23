package nu.marginalia.service.discovery;

import nu.marginalia.service.discovery.monitor.ServiceMonitorIf;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.ServiceId;
import nu.marginalia.test.TestApiGrpc;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
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
    @Disabled // flaky on CI
    void getPort() {
        System.setProperty("service.random-port", "true");

        var registry1 = createRegistry();
        var registry2 = createRegistry();

        List<Integer> ports = new ArrayList<>();
        Set<Integer> portsSet = new HashSet<>();

        var key = ServiceKey.forRest(ServiceId.Search, 0);

        for (int i = 0; i < 500; i++) {
            int port = registry1.requestPort("127.0.0.1", key);
            ports.add(port);

            // Ensure we get unique ports
            assertTrue(portsSet.add(port));
        }
        for (int i = 0; i < 50; i++) {
            int port = registry2.requestPort("127.0.0.1", key);
            ports.add(port);

            // Ensure we get unique ports
            assertTrue(portsSet.add(port));
        }
        registry1.shutDown();
        for (int i = 0; i < 500; i++) {
            // Verify we can reclaim ports
            ports.add(registry2.requestPort("127.0.0.1", key));
        }
        assertEquals(1050, ports.size());
    }

    @Test
    void getInstancesRestgRPC() throws Exception {
        var uuid1 = UUID.randomUUID();
        var uuid2 = UUID.randomUUID();

        var registry1 = createRegistry();
        var registry2 = createRegistry();

        var key1 = ServiceKey.forRest(ServiceId.Search, 0);
        var key2 = ServiceKey.forGrpcApi(TestApiGrpc.class, ServicePartition.any());

        var endpoint1 = registry1.registerService(key1, uuid1, "127.0.0.1");
        var endpoint2 = registry2.registerService(key2, uuid2, "127.0.0.2");

        registry1.announceInstance(uuid1);
        registry2.announceInstance(uuid2);

        assertEquals(List.of(endpoint1.asInstance(uuid1)),
                registry1.getEndpoints(key1));

        assertEquals(List.of(endpoint2.asInstance(uuid2)),
                registry1.getEndpoints(key2));

        registry1.shutDown();
        Thread.sleep(100);

        assertEquals(List.of(), registry2.getEndpoints(key1));
        assertEquals(List.of(endpoint2.asInstance(uuid2)), registry2.getEndpoints(key2));
    }

    @Test
    void testInstancesTwoAny() throws Exception {
        var uuid1 = UUID.randomUUID();
        var uuid2 = UUID.randomUUID();

        var registry1 = createRegistry();
        var registry2 = createRegistry();

        var key = ServiceKey.forGrpcApi(TestApiGrpc.class, ServicePartition.any());

        var endpoint1 = registry1.registerService(key, uuid1, "127.0.0.1");
        var endpoint2 = registry2.registerService(key, uuid2, "127.0.0.2");

        registry1.announceInstance(uuid1);
        registry2.announceInstance(uuid2);

        assertEquals(Set.of(endpoint1.asInstance(uuid1),
                        endpoint2.asInstance(uuid2)),
                new HashSet<>(registry1.getEndpoints(key)));

        registry1.shutDown();
        Thread.sleep(100);

        assertEquals(List.of(endpoint2.asInstance(uuid2)), registry2.getEndpoints(key));
    }

    @Test
    void testInstancesTwoPartitions() throws Exception {
        var uuid1 = UUID.randomUUID();
        var uuid2 = UUID.randomUUID();

        var registry1 = createRegistry();
        var registry2 = createRegistry();

        var key1 = ServiceKey.forGrpcApi(TestApiGrpc.class, ServicePartition.partition(1));
        var key2 = ServiceKey.forGrpcApi(TestApiGrpc.class, ServicePartition.partition(2));

        var endpoint1 = registry1.registerService(key1, uuid1, "127.0.0.1");
        var endpoint2 = registry2.registerService(key2, uuid2, "127.0.0.2");

        registry1.announceInstance(uuid1);
        registry2.announceInstance(uuid2);

        assertEquals(List.of(endpoint1.asInstance(uuid1)), registry1.getEndpoints(key1));
        assertEquals(List.of(endpoint2.asInstance(uuid2)), registry1.getEndpoints(key2));
    }

    @Test
    public void announceLiveness() throws Exception {
        var registry1 = createRegistry();
        var uuid1 = UUID.randomUUID();

        assertFalse(registry1.isInstanceRunning(uuid1));
        registry1.announceInstance(uuid1);
        assertTrue(registry1.isInstanceRunning(uuid1));

        registry1.shutDown();
    }

    @Test
    public void testMonitors() throws Exception {
        var registry1 = createRegistry();
        var registry2 = createRegistry();
        var uuid1 = UUID.randomUUID();
        var uuid2 = UUID.randomUUID();

        var monitor = Mockito.mock(ServiceMonitorIf.class);
        when(monitor.getKey()).thenReturn((ServiceKey)ServiceKey.forGrpcApi(
                TestApiGrpc.class,
                ServicePartition.any()));

        // This test has sleeps that make it a bit
        // easier to reason about, as it lets us pretend
        // a distributed concurrent system is not.

        registry1.registerMonitor(monitor);

        Thread.sleep(100);
        registry2.announceInstance(uuid1);
        Thread.sleep(100);
        registry1.announceInstance(uuid2);
        Thread.sleep(100);
        registry1.shutDown();

        Thread.sleep(100);

        Mockito.verify(monitor, Mockito.times(3)).onChange();

    }
}