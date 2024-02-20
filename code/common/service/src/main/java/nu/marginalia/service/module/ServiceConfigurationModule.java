package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.service.discovery.FixedServiceRegistry;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.ZkServiceRegistry;
import nu.marginalia.service.id.ServiceId;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class ServiceConfigurationModule extends AbstractModule {
    private final ServiceId id;
    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigurationModule.class);

    public ServiceConfigurationModule(ServiceId id) {
        this.id = id;
    }

    public void configure() {
        int node = getNode();

        var configObject = new ServiceConfiguration(id,
                node,
                getBindAddress(),
                getExternalHost(),
                getPrometheusPort(),
                UUID.randomUUID()
        );

        logger.info("Service configuration: {}", configObject);

        bind(Integer.class).annotatedWith(Names.named("wmsa-system-node")).toInstance(node);
        bind(ServiceConfiguration.class).toInstance(configObject);

        if (Boolean.getBoolean("system.useZookeeper")) {
            CuratorFramework client = CuratorFrameworkFactory
                    .newClient(System.getProperty("zookeeper-hosts", "zookeeper:2181"),
                            new ExponentialBackoffRetry(100, 10, 1000));

            bind(CuratorFramework.class).toInstance(client);
            bind(ServiceRegistryIf.class).to(ZkServiceRegistry.class);
        }
        else {
            bind(ServiceRegistryIf.class).to(FixedServiceRegistry.class);
        }
    }

    private int getPrometheusPort() {
        String prometheusPortEnv = System.getenv("WMSA_PROMETHEUS_PORT");

        if (prometheusPortEnv != null) {
            return Integer.parseInt(prometheusPortEnv);
        }

        return 7000;
    }

    private int getNode() {
        String nodeEnv = Objects.requireNonNullElse(System.getenv("WMSA_SERVICE_NODE"), "0");

        return Integer.parseInt(nodeEnv);
    }

    /** Get the external host for the service. This is announced via the service registry,
     * and should be an IP address or hostname that resolves to this machine */
    private String getExternalHost() {
        // Check for an environment variable override
        String configuredValue;
        if (null != (configuredValue = System.getenv("SERVICE_HOST"))) {
            return configuredValue;
        }

        // Check for a system property override
        if (null != (configuredValue = System.getProperty("service.host"))) {
            return configuredValue;
        }

        // If we're in docker, we'll use the hostname
        if (isDocker()) {
            return System.getenv("HOSTNAME");
        }

        // If we've not been told about a host, and we're not in docker, we'll fall back to localhost
        // and hope the operator's remembered to enable random port assignment via zookeeper
        return "127.0.0.1";
    }

    /** Get the bind address for the service. This is the address that the service will listen on.
     */
    private String getBindAddress() {
        String configuredValue = System.getProperty("service.bind-address");
        if (configuredValue != null) {
            return configuredValue;
        }

        // If we're in docker, we'll bind to all interfaces
        if (isDocker())
            return "0.0.0.0";
        else  // If we're not in docker, we'll default to binding to localhost to avoid exposing services
            return "127.0.0.1";
    }

    boolean isDocker() {
        return System.getenv("WMSA_IN_DOCKER") != null;
    }
}
