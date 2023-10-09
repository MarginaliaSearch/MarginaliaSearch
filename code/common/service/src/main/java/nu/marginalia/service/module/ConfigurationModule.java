package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.Objects;
import java.util.UUID;

public class ConfigurationModule extends AbstractModule {
    private final ServiceDescriptors descriptors;
    private final ServiceId id;

    public ConfigurationModule(ServiceDescriptors descriptors, ServiceId id) {
        this.descriptors = descriptors;
        this.id = id;
    }

    public void configure() {
        bind(ServiceDescriptors.class).toInstance(descriptors);

        var configObject = new ServiceConfiguration(id,
                getNode(),
                getHost(),
                getBasePort(),
                getPrometheusPort(),
                UUID.randomUUID()
        );

        bind(ServiceConfiguration.class).toInstance(configObject);
    }

    private int getBasePort() {
        String port = System.getenv("WMSA_SERVICE_PORT");

        if (port != null) {
            return Integer.parseInt(port);
        }

        return descriptors.forId(id).port;
    }

    private int getPrometheusPort() {
        String prometheusPortEnv = System.getenv("WMSA_PROMETHEUS_PORT");

        if (prometheusPortEnv != null) {
            return Integer.parseInt(prometheusPortEnv);
        }

        return descriptors.forId(id).port + 1000;
    }

    private int getNode() {
        String nodeEnv = Objects.requireNonNullElse(System.getenv("WMSA_SERVICE_NODE"), "0");

        return Integer.parseInt(nodeEnv);
    }

    private String getHost() {
        return System.getProperty("service-host", "127.0.0.1");
    }

}
