package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.Objects;

public class ConfigurationModule extends AbstractModule {
    private static final String SERVICE_NAME = System.getProperty("service-name");
    private final ServiceDescriptors descriptors;
    private final ServiceId id;

    public ConfigurationModule(ServiceDescriptors descriptors, ServiceId id) {
        this.descriptors = descriptors;
        this.id = id;
    }

    public void configure() {
        bind(ServiceDescriptors.class).toInstance(descriptors);
        bind(String.class).annotatedWith(Names.named("service-name")).toInstance(Objects.requireNonNull(SERVICE_NAME));
        bind(String.class).annotatedWith(Names.named("service-host")).toInstance(System.getProperty("service-host", "127.0.0.1"));
        bind(Integer.class).annotatedWith(Names.named("service-port")).toInstance(descriptors.forId(id).port);
    }

    @Provides
    @Named("metrics-server-port")
    public Integer provideMetricsServerPort(@Named("service-port") Integer servicePort) {
        return servicePort + 1000;
    }

}
