package nu.marginalia.service.discovery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.discovery.monitor.ServiceMonitorIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceKey;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static nu.marginalia.service.discovery.property.ServiceEndpoint.InstanceAddress;

/** A versatile service registry that uses ZooKeeper to store service endpoints.
 * It is used to register services and to look up the endpoints of other services.
 * <p></p>
 * It may also be used to assign ports to services, if the system property
 * <code>service.random-port</code> is set to <code>true</code>.  This is useful
 * for running the system in a bare-metal environment, where the ports are not
 * managed by Docker, and there are enough services that managing them manually
 * will be a serious headache.
 * */
@Singleton
public class ZkServiceRegistry implements ServiceRegistryIf {
    private final CuratorFramework curatorFramework;
    private static final Logger logger = LoggerFactory.getLogger(ZkServiceRegistry.class);
    private volatile boolean stopped = false;

    private final List<CuratorCache> curatorCaches = new ArrayList<>();
    private final List<String> livenessPaths = new ArrayList<>();
    @Inject
    public ZkServiceRegistry(CuratorFramework curatorFramework) {
        try {
            this.curatorFramework = curatorFramework;
            curatorFramework.start();
            if (!curatorFramework.blockUntilConnected(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to connect to zookeeper after 30s");
            }

            Runtime.getRuntime().addShutdownHook(
                    new Thread(this::shutDown, "ZkServiceRegistry shutdown hook")
            );
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to start ZkServiceRegistry", ex);
        }
    }

    @Override
    public ServiceEndpoint registerService(ServiceKey<?> key,
                                           UUID instanceUUID,
                                           String externalAddress)
    throws Exception
    {
        var endpoint = new ServiceEndpoint(externalAddress, requestPort(externalAddress, key));

        String path = key.toPath() + "/" + instanceUUID.toString();
        byte[] payload = (endpoint.host() + ":" + endpoint.port()).getBytes(StandardCharsets.UTF_8);

        logger.info("Registering {} -> {}", path, endpoint);

        curatorFramework.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(path, payload);

        return endpoint;
    }

    @Override
    public void declareFirstBoot() {
        if (!isFirstBoot()) {
            try {
                curatorFramework.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .forPath("/first-boot");
            }
            catch (Exception ex) {
                logger.error("Failed to declare first-boot", ex);
            }
        }
    }

    @Override
    public void waitForFirstBoot() throws InterruptedException {
        if (!isFirstBoot())
            logger.info("Waiting for first-boot flag");

        while (true) {
            if (isFirstBoot())
                return;

            Thread.sleep(1000);
        }
    }

    private boolean isFirstBoot() {
        try {
            return curatorFramework.checkExists().forPath("/first-boot") != null;
        }
        catch (Exception ex) {
            logger.error("Failed to check first-boot", ex);
            return false;
        }
    }

    @Override
    public void announceInstance(UUID instanceUUID) {
        try {
            String serviceRoot = "/running-instances/" + instanceUUID.toString();

            livenessPaths.add(serviceRoot);

            curatorFramework.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(serviceRoot);
        }
        catch (Exception ex) {
            logger.error("Failed to create service root for {}", instanceUUID);
        }
    }

    /**
     * Returns true if the service has announced itself as up and running.
     */
    public boolean isInstanceRunning(UUID instanceUUID) {
        try {
            String serviceRoot = "/running-instances/" + instanceUUID.toString();
            return null != curatorFramework.checkExists().forPath(serviceRoot);
        }
        catch (Exception ex) {
            logger.error("Failed to check if instance is running {}", instanceUUID);
            return false;
        }
    }

    @Override
    public int requestPort(String externalHost,
                           ServiceKey<?> key) {

        if (!Boolean.getBoolean("service.random-port")) {
            return switch (key) {
                case ServiceKey.Rest rest -> Integer.getInteger("service.http-port", 80);
                case ServiceKey.Grpc<?> grpc -> Integer.getInteger("service.grpc-port",81);
            };
        }


        int portRangeLow = 12_000;
        int portRangeHigh = 12_999;

        var random = new Random();

        String identifier = key.toPath();

        byte[] payload = identifier.getBytes();

        for (int iter = 0; iter < 1000; iter++) {
            try {
                int port = random.nextInt(portRangeLow, portRangeHigh);

                curatorFramework.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .forPath("/port-registry/" + externalHost + "/" + port, payload);
                return port;
            }
            catch (Exception ex) {
                logger.error("Still negotiating port for " + identifier);
            }
        }

        throw new IllegalStateException("Failed to negotiate a port for host " + externalHost);
    }

    @Override
    public List<InstanceAddress> getEndpoints(ServiceKey<?> key) {
        try {
            List<InstanceAddress> ret = new ArrayList<>();
            for (var uuid : curatorFramework
                    .getChildren()
                    .forPath(key.toPath())) {

                if (!isInstanceRunning(UUID.fromString(uuid))) {
                    continue;
                }

                var path = ZKPaths.makePath(key.toPath(), uuid);
                byte[] data = curatorFramework
                        .getData()
                        .forPath(path);

                String hostAndPort = new String(data);
                var address = ServiceEndpoint
                        .parse(hostAndPort)
                        .asInstance(UUID.fromString(uuid));

                ret.add(address);
            }

            return ret;
        }
        catch (Exception ex) {
            return List.of();
        }
    }


    public void registerMonitor(ServiceMonitorIf monitor) {
        if (stopped)
            logger.info("Not registering monitor for {} because the registry is stopped", monitor.getKey());


        CuratorCache pathCache = CuratorCache.builder(curatorFramework, monitor.getKey().toPath()).build();
        curatorCaches.add(pathCache);

        pathCache.listenable()
                .addListener(CuratorCacheListener.builder()
                        .forDeletes((_) -> monitor.onChange())
                        .forCreatesAndChanges((_,_) -> monitor.onChange())
                        .build()
                );

        CuratorCache instanceCache = CuratorCache.builder(curatorFramework, "/running-instances").build();
        curatorCaches.add(instanceCache);
        instanceCache.listenable().addListener(
                CuratorCacheListener.builder()
                        .forDeletes(_ -> monitor.onChange())
                        .forCreates(_ -> monitor.onChange())
                        .build()
        );
    }

    @Override
    public void registerProcess(String processName, int nodeId) {
        String path = "/process-locks/" + processName + "/" + nodeId;
        try {
            curatorFramework.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(path);
            livenessPaths.add(path);
        }
        catch (Exception ex) {
            logger.error("Failed to register process {} on node {}", processName, nodeId, ex);
        }
    }

    @Override
    public void deregisterProcess(String processName, int nodeId) {
        String path = "/process-locks/" + processName + "/" + nodeId;
        try {
            curatorFramework.delete().forPath(path);
            livenessPaths.remove(path);
        }
        catch (Exception ex) {
            logger.error("Failed to deregister process {} on node {}", processName, nodeId, ex);
        }
    }

    @Override
    public InterProcessSemaphoreV2 getSemaphore(String name, int permits) {
        if (stopped)
            throw new IllegalStateException("Service registry is stopped, cannot get semaphore " + name);

        String path = "/semaphores/" + name;
        return new InterProcessSemaphoreV2(curatorFramework, path, permits);
    }

    /* Exposed for tests */
    public synchronized void shutDown() {
        if (stopped)
            return;

        stopped = true;

        for (var cache: curatorCaches) {
            cache.close();
        }

        // Delete all liveness paths
        for (var path : livenessPaths) {
            logger.info("Cleaning up {}", path);

            try {
                curatorFramework.delete().forPath(path);
            }
            catch (Exception ex) {
                logger.error("Failed to delete path {}", path, ex);
            }
        }
    }
}
