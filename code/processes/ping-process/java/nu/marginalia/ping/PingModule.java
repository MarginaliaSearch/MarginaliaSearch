package nu.marginalia.ping;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import nu.marginalia.ping.io.HttpClientProvider;
import nu.marginalia.ping.model.ErrorClassification;
import org.apache.hc.client5.http.classic.HttpClient;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PingModule extends AbstractModule {

    public PingModule() throws NoSuchAlgorithmException {
    }

    public static PingIntervalsConfiguration createPingIntervalsConfiguration() {
        Map<ErrorClassification, Duration> initialTimeouts = new HashMap<>();
        Map<ErrorClassification, Duration> maxTimeouts = new HashMap<>();

        for (var classification : ErrorClassification.values()) {
            switch (classification) {
                case CONNECTION_ERROR -> {
                    initialTimeouts.put(classification, Duration.ofMinutes(15));
                    maxTimeouts.put(classification, Duration.ofDays(1));
                }
                case HTTP_CLIENT_ERROR -> {
                    initialTimeouts.put(classification, Duration.ofMinutes(15));
                    maxTimeouts.put(classification, Duration.ofDays(1));
                }
                case HTTP_SERVER_ERROR -> {
                    initialTimeouts.put(classification, Duration.ofMinutes(8));
                    maxTimeouts.put(classification, Duration.ofHours(6));
                }
                case SSL_ERROR -> {
                    initialTimeouts.put(classification, Duration.ofMinutes(45));
                    maxTimeouts.put(classification, Duration.ofDays(1));
                }
                case DNS_ERROR -> {
                    initialTimeouts.put(classification, Duration.ofMinutes(60));
                    maxTimeouts.put(classification, Duration.ofDays(7));
                }
                case TIMEOUT -> {
                    initialTimeouts.put(classification, Duration.ofMinutes(5));
                    maxTimeouts.put(classification, Duration.ofHours(6));
                }
                case UNKNOWN -> {
                    initialTimeouts.put(classification, Duration.ofMinutes(30));
                    maxTimeouts.put(classification, Duration.ofDays(1));
                }
                case NONE -> {
                    initialTimeouts.put(classification, Duration.ofHours(6));
                    maxTimeouts.put(classification, Duration.ofDays(6));
                }
            }
        }

        return new PingIntervalsConfiguration(
                Duration.ofHours(3),
                initialTimeouts,
                maxTimeouts
        );
    }

    @Override
    protected void configure() {
        bind(HttpClient.class).toProvider(HttpClientProvider.class);

        bind(PingIntervalsConfiguration.class).toInstance(createPingIntervalsConfiguration());
    }

    @Provides
    @Named("ping.nameservers")
    public List<String> providePingNameservers() {
        // Google's public DNS servers currently have the best rate limiting
        return List.of("8.8.8.8", "8.8.4.4");
    }
}
