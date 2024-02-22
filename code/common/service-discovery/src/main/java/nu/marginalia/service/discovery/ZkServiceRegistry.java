package nu.marginalia.service.discovery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.service.discovery.monitor.*;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import static nu.marginalia.service.discovery.property.ServiceEndpoint.*;

import nu.marginalia.service.discovery.property.ServiceKey;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    private final List<String> livenessPaths = new ArrayList<>();

    @Inject
    @SneakyThrows
    public ZkServiceRegistry(CuratorFramework curatorFramework) {
        this.curatorFramework = curatorFramework;

        curatorFramework.start();
        if (!curatorFramework.blockUntilConnected(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Failed to connect to zookeeper after 30s");
        }

        Runtime.getRuntime().addShutdownHook(
                new Thread(this::shutDown, "ZkServiceRegistry shutdown hook")
        );
    }

    @Override
    public ServiceEndpoint registerService(ServiceKey<?> key,
                                           UUID instanceUUID,
                                           String externalAddress)
    throws Exception
    {
        var endpoint = new ServiceEndpoint(externalAddress, requestPort(externalAddress, key));

        String path = STR."\{key.toPath()}/\{instanceUUID.toString()}";
        byte[] payload = STR."\{endpoint.host()}:\{endpoint.port()}".getBytes(StandardCharsets.UTF_8);

        logger.info("Registering {} -> {}", path, endpoint);

        curatorFramework.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(path, payload);

        return endpoint;
    }

    @SneakyThrows
    @Override
    public void declareFirstBoot() {
        if (!isFirstBoot()) {
            curatorFramework.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(STR."/first-boot");
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
            String serviceRoot = STR."/running-instances/\{instanceUUID.toString()}";

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
            String serviceRoot = STR."/running-instances/\{instanceUUID.toString()}";
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
                case ServiceKey.Rest rest -> 80;
                case ServiceKey.Grpc<?> grpc -> 81;
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
                        .forPath(STR."/port-registry/\{externalHost}/\{port}", payload);
                return port;
            }
            catch (Exception ex) {
                logger.error(STR."Still negotiating port for \{identifier}");
            }
        }

        throw new IllegalStateException("Failed to negotiate a port for host " + externalHost);
    }

    @Override
    public Set<InstanceAddress> getEndpoints(ServiceKey<?> key) {
        try {
            Set<InstanceAddress> ret = new HashSet<>();
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

                long cxTime = curatorFramework.checkExists().forPath(path).getMzxid();

                String hostAndPort = new String(data);
                var address = ServiceEndpoint
                        .parse(hostAndPort)
                        .asInstance(UUID.fromString(uuid), cxTime);

                ret.add(address);
            }

            return ret;
        }
        catch (Exception ex) {
            return Set.of();
        }
    }

    public void registerMonitor(ServiceMonitorIf monitor) throws Exception {
        if (stopped)
            logger.info("Not registering monitor for {} because the registry is stopped", monitor.getKey());

        String path = monitor.getKey().toPath();

        CuratorWatcher watcher = change -> {
            boolean reRegister;
            try {
                reRegister = monitor.onChange();
            }
            catch (Exception ex) {
                logger.error("Monitor for path {} failed", path, ex);
                reRegister = true;
            }

            if (reRegister) {
                registerMonitor(monitor);
            }
        };

        curatorFramework.watchers().add()
                .usingWatcher(watcher)
                .forPath(path);

        // Also register for updates to the running-instances list,
        // as this will have an effect on the result of getEndpoints()
        curatorFramework.watchers().add()
                .usingWatcher(watcher)
                .forPath("/running-instances");
    }

    /* Exposed for tests */
    public synchronized void shutDown() {
        if (!stopped)
            return;

        stopped = true;

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
