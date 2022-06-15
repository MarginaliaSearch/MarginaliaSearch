package nu.marginalia.wmsa.configuration.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;

import java.util.Objects;

import static com.google.inject.name.Names.named;

public class ConfigurationModule extends AbstractModule {
    private static final String SERVICE_NAME = System.getProperty("service-name");

    public void configure() {
        bind(String.class).annotatedWith(named("service-name")).toInstance(Objects.requireNonNull(SERVICE_NAME));
        bind(String.class).annotatedWith(named("service-host")).toInstance(System.getProperty("service-host", "127.0.0.1"));
        bind(Integer.class).annotatedWith(named("service-port")).toInstance(ServiceDescriptor.byName(System.getProperty("service-name")).port);
    }

    @Provides
    @Named("metrics-server-port")
    public Integer provideMetricsServerPort(@Named("service-port") Integer servicePort) {
        return servicePort + 1000;
    }

}
