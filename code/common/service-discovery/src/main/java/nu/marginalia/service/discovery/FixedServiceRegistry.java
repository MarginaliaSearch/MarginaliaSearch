package nu.marginalia.service.discovery;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.service.discovery.monitor.*;
import nu.marginalia.service.discovery.property.ApiSchema;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceEndpoint.GrpcEndpoint;
import nu.marginalia.service.discovery.property.ServiceEndpoint.InstanceAddress;
import nu.marginalia.service.discovery.property.ServiceEndpoint.RestEndpoint;
import nu.marginalia.service.id.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A service registry that returns fixed endpoints for all services.
 * <p></p>
 * This is for backwards-compatibility with old docker-compose files with no
 * ZooKeeper configured.
 * */
public class FixedServiceRegistry implements ServiceRegistryIf {
    private static final Logger logger = LoggerFactory.getLogger(FixedServiceRegistry.class);

    private final HikariDataSource dataSource;

    @Inject
    public FixedServiceRegistry(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public ServiceEndpoint registerService(ApiSchema schema, ServiceId id, int node, UUID instanceUUID, String externalAddress) throws Exception {
        return switch (schema) {
            case REST -> new ServiceEndpoint.RestEndpoint(externalAddress, 80);
            case GRPC -> new ServiceEndpoint.GrpcEndpoint(externalAddress, 81);
        };
    }

    @Override
    public void announceInstance(ServiceId id, int node, UUID instanceUUID) {
        // No-op
    }

    @Override
    public Set<Integer> getServiceNodes(ServiceId id) {

        if (id == ServiceId.Executor || id == ServiceId.Index) {
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement("SELECT ID FROM NODE_CONFIGURATION")) {
                Set<Integer> ret = new HashSet<>();
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    ret.add(rs.getInt(1));
                }
                return ret;
            }
            catch (SQLException ex) {
                return Set.of();
            }
        }

        else return Set.of(0);
    }

    @Override
    public int requestPort(String externalHost, ApiSchema schema, ServiceId id, int node) {
        return switch(schema) {
            case REST -> 80;
            case GRPC -> 81;
        };
    }

    @Override
    public Set<InstanceAddress<? extends ServiceEndpoint>> getEndpoints(ApiSchema schema, ServiceId id, int node) {
        return switch (schema) {
            case REST -> Set.of(new InstanceAddress<>(
                        new RestEndpoint(id.serviceName + "-" + node, 80),
                            UUID.randomUUID()));
            case GRPC -> Set.of(new InstanceAddress<>(
                        new GrpcEndpoint(id.serviceName + "-" + node, 81),
                            UUID.randomUUID()));
        };
    }

    public void registerMonitor(ServiceMonitorIf monitor) throws Exception {
        // We don't have any notification mechanism, so we just periodically
        // invoke the monitor's onChange method to simulate it.

        periodicallyInvoke(monitor, Duration.ofSeconds(15));
    }


    void periodicallyInvoke(ServiceMonitorIf monitor, Duration d) {
        Thread.ofPlatform().name("PeriodicInvoker").start(() -> {
            for (;;) {
                try {
                    Thread.sleep(d);
                } catch (InterruptedException e) {
                    break;
                }

                boolean reRegister;
                try {
                    reRegister = monitor.onChange();
                }
                catch (Exception ex) {
                    logger.error("Monitor failed", ex);
                    reRegister = true;
                }

                if (!reRegister) {
                    break;
                }
            }
        });
    }
}
