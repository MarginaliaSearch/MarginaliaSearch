package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.Objects;
import java.util.UUID;

public class ServiceConfigurationModule extends AbstractModule {
    private final ServiceDescriptors descriptors;
    private final ServiceId id;

    public ServiceConfigurationModule(ServiceDescriptors descriptors, ServiceId id) {
        this.descriptors = descriptors;
        this.id = id;
    }

    public void configure() {
        bind(ServiceDescriptors.class).toInstance(descriptors);

        int node = getNode();

        var configObject = new ServiceConfiguration(id,
                node,
                getHost(),
                getBasePort(),
                getPrometheusPort(),
                UUID.randomUUID()
        );

        bind(Integer.class).annotatedWith(Names.named("wmsa-system-node")).toInstance(node);
        bind(ServiceConfiguration.class).toInstance(configObject);
    }

    private int getBasePort() {
        String port = System.getenv("WMSA_SERVICE_PORT");

        if (port != null) {
            return Integer.parseInt(port);
        }

        return 80;
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
        return System.getProperty("service-host", "127.0.0.1");
    }

}
