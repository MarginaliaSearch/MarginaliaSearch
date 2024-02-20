package nu.marginalia.service.discovery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.service.discovery.monitor.*;
import nu.marginalia.service.discovery.property.ApiSchema;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import static nu.marginalia.service.discovery.property.ServiceEndpoint.*;
import nu.marginalia.service.id.ServiceId;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    public ServiceEndpoint registerService(ApiSchema schema, ServiceId id,
                                           int node,
                                           UUID instanceUUID,
                                           String externalAddress)
    throws Exception
    {
        var ephemeralProperty = curatorFramework.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL);

        var endpoint = ServiceEndpoint.forSchema(schema, externalAddress,
                requestPort(externalAddress, schema, id, node)
        );

        String path;
        byte[] payload;

        switch (endpoint) {
            case ServiceEndpoint.GrpcEndpoint(String host, int port) -> {
                path = STR."/services/\{id.serviceName}/\{node}/grpc/\{instanceUUID.toString()}";
                payload = STR."\{host}:\{port}".getBytes(StandardCharsets.UTF_8);
            }
            case ServiceEndpoint.RestEndpoint(String host, int port) -> {
                path = STR."/services/\{id.serviceName}/\{node}/rest/\{instanceUUID.toString()}";
                payload = STR."\{host}:\{port}".getBytes(StandardCharsets.UTF_8);
            }
        }

        logger.info("Registering {} -> {}", path, endpoint);

        ephemeralProperty.forPath(path, payload);

        return endpoint;
    }

    @Override
    public void announceInstance(ServiceId id, int node, UUID instanceUUID) {
        try {
            String serviceRoot = STR."/services/\{id.serviceName}/\{node}/running/\{instanceUUID.toString()}";
            curatorFramework.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(serviceRoot);
        }
        catch (Exception ex) {
            logger.error("Failed to create service root for {}", id.serviceName);
        }
    }

    /**
     * Returns true if the service has announced itself as up and running.
     */
    public boolean isInstanceRunning(ServiceId id, int node, UUID instanceUUID) {
        try {
            String serviceRoot = STR."/services/\{id.serviceName}/\{node}/running/\{instanceUUID.toString()}";
            return null != curatorFramework.checkExists().forPath(serviceRoot);
        }
        catch (Exception ex) {
            logger.error("Failed to check if service is running {}", id.serviceName);
            return false;
        }
    }

    @Override
    public Set<Integer> getServiceNodes(ServiceId id) {
        try {
            String serviceRoot = STR."/services/\{id.serviceName}";
            return curatorFramework.getChildren().forPath(serviceRoot)
                    .stream().map(Integer::parseInt)
                    .collect(Collectors.toSet());
        }
        catch (Exception ex) {
            logger.error("Failed to get nodes for service {}", id.serviceName);
            return Set.of();
        }
    }

    @Override
    public int requestPort(String externalHost,
                           ApiSchema schema,
                           ServiceId id,
                           int node)
    {
        if (!Boolean.getBoolean("service.random-port")) {
            return switch(schema) {
                case REST -> 80;
                case GRPC -> 81;
            };
        }

        int portRangeLow = 12_000;
        int portRangeHigh = 12_999;

        var random = new Random();

        String host = STR."\{id.serviceName}-\{node}";

        byte[] payload = STR."\{schema}://\{host}".getBytes(StandardCharsets.UTF_8);

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
                logger.error(STR."Still negotiating port for \{schema}://\{id.serviceName}:\{node}");
            }
        }

        throw new IllegalStateException("Failed to negotiate a port for host " + externalHost);
    }

    @Override
    public Set<InstanceAddress<? extends ServiceEndpoint>> getEndpoints(ApiSchema schema, ServiceId id, int node) {
        return switch (schema) {
            case REST -> getRestEndpoints(id, node);
            case GRPC -> getGrpcEndpoints(id, node);
        };
    }

    public Set<InstanceAddress<? extends ServiceEndpoint>> getRestEndpoints(ServiceId id, int node) {
        try {
            Set<InstanceAddress<? extends ServiceEndpoint>> ret = new HashSet<>();
            String restRoot = STR."/services/\{id.serviceName}/\{node}/rest";
            for (var uuid : curatorFramework
                    .getChildren()
                    .forPath(restRoot)) {

                if (!isInstanceRunning(id, node, UUID.fromString(uuid))) {
                    continue;
                }

                byte[] data = curatorFramework
                        .getData()
                        .forPath(ZKPaths.makePath(restRoot, uuid));
                String hostAndPort = new String(data);
                var address = RestEndpoint
                        .parse(hostAndPort)
                        .asInstance(UUID.fromString(uuid));

                // Ensure that the address is resolvable
                // (this reduces the risk of exceptions when trying to connect to the service)
                if (!address.endpoint().validateHost()) {
                    logger.warn("Omitting stale address {}, address does not resolve", address);
                    continue;
                }

                ret.add(address);

            }

            return ret;
        }
        catch (Exception ex) {
            return Set.of();
        }
    }

    public Set<InstanceAddress<? extends ServiceEndpoint>> getGrpcEndpoints(ServiceId id, int node) {
        try {
            Set<InstanceAddress<? extends ServiceEndpoint>> ret = new HashSet<>();
            String restRoot = STR."/services/\{id.serviceName}/\{node}/grpc";
            for (var uuid : curatorFramework
                    .getChildren()
                    .forPath(restRoot)) {

                if (!isInstanceRunning(id, node, UUID.fromString(uuid))) {
                    continue;
                }

                byte[] data = curatorFramework
                        .getData()
                        .forPath(ZKPaths.makePath(restRoot, uuid));

                String hostAndPort = new String(data);
                var address = GrpcEndpoint
                        .parse(hostAndPort)
                        .asInstance(UUID.fromString(uuid));

                // Ensure that the address is resolvable
                // (this reduces the risk of exceptions when trying to connect to the service)
                if (!address.endpoint().validateHost()) {
                    logger.warn("Omitting stale address {}, address does not resolve", address);
                    continue;
                }

                ret.add(address);

            }

            return ret;
        }
        catch (Exception ex) {
            return Set.of();
        }
    }

    public void registerMonitor(ServiceMonitorIf monitor) throws Exception {
        monitor.register(this);
    }

    public void registerMonitor(ServiceChangeMonitor monitor) throws Exception {
        installMonitor(monitor, STR."/services/\{monitor.serviceId.serviceName}");
    }

    public void registerMonitor(ServiceNodeChangeMonitor monitor) throws Exception {
        installMonitor(monitor, STR."/services/\{monitor.serviceId.serviceName}/\{monitor.node}");
    }

    public void registerMonitor(ServiceRestEndpointChangeMonitor monitor) throws Exception {
        installMonitor(monitor, STR."/services/\{monitor.serviceId.serviceName}/\{monitor.node}/rest");
    }

    public void registerMonitor(ServiceGrpcEndpointChangeMonitor monitor) throws Exception {
        installMonitor(monitor, STR."/services/\{monitor.serviceId.serviceName}/\{monitor.node}/grpc");
    }

    private void installMonitor(ServiceMonitorIf monitor, String path) throws Exception {
        CuratorWatcher watcher = _ -> {
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
    }

    /* Exposed for tests */
    public synchronized void shutDown() {
        if (!stopped) {
            curatorFramework.close();
            stopped = true;
        }
    }
}
