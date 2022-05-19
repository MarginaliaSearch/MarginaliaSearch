package nu.marginalia.wmsa.configuration.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.SneakyThrows;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static com.google.inject.name.Names.named;

public class ConfigurationModule extends AbstractModule {
    private static final String SERVICE_NAME = System.getProperty("service-name");
    public static final int MONITOR_PORT = Integer.getInteger("monitor.port", 5000);
    public static final String MONITOR_HOST = System.getProperty("monitor.host", "127.0.0.1");

    public void configure() {
        bind(Integer.class).annotatedWith(named("monitor-port")).toInstance(MONITOR_PORT);
        bind(String.class).annotatedWith(named("monitor-host")).toInstance(MONITOR_HOST);
        bind(Integer.class).annotatedWith(named("monitor-boot-timeout")).toInstance(10);

        bind(String.class).annotatedWith(named("service-name")).toInstance(Objects.requireNonNull(SERVICE_NAME));
        bind(String.class).annotatedWith(named("service-host")).toProvider(HostnameProvider.class).in(Singleton.class);
        bind(Integer.class).annotatedWith(named("service-port")).toProvider(PortProvider.class).in(Singleton.class);
        bind(Integer.class).annotatedWith(named("metrics-server-port")).toProvider(MetricsPortProvider.class).in(Singleton.class);

    }

    @Provides
    @Named("build-version")
    @SneakyThrows
    public String buildVersion() {
        try (var str = ClassLoader.getSystemResourceAsStream("_version.txt")) {
            if (null == str) {
                System.err.println("Missing _version.txt from classpath");
                return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            return new String(str.readAllBytes());
        }
    }

}
