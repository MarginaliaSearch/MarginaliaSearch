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

import java.util.Objects;
import java.util.UUID;

public class ServiceConfigurationModule extends AbstractModule {
    private final ServiceId id;

    public ServiceConfigurationModule(ServiceId id) {
        this.id = id;
    }

    public void configure() {
        int node = getNode();

        var configObject = new ServiceConfiguration(id,
                node,
                getBindAddress(),
                getHost(),
                getPrometheusPort(),
                UUID.randomUUID()
        );

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

    private String getHost() {
        int node = getNode();
        final String defaultValue;

        if (node > 0) defaultValue = STR."\{id.serviceName}-\{node}";
        else defaultValue = id.serviceName;

        return System.getProperty("service.host", defaultValue);
    }

    private String getBindAddress() {
        return System.getProperty("service.bind-address", "0.0.0.0");
    }

}
