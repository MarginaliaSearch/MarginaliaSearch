package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
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

        int basePort = descriptors.forId(id).port;
        int prometheusPort = basePort + 1000;
        String host = Objects.requireNonNull(System.getProperty("service-host", "127.0.0.1"));
        var configObject = new ServiceConfiguration(id, 0, host, basePort, prometheusPort, UUID.randomUUID());

        bind(ServiceConfiguration.class).toInstance(configObject);
    }

}
