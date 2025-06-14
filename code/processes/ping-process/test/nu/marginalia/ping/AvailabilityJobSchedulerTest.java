package nu.marginalia.ping;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.coordination.LocalDomainCoordinator;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.ping.fetcher.PingDnsFetcher;
import nu.marginalia.ping.fetcher.PingHttpFetcher;
import nu.marginalia.ping.io.HttpClientProvider;
import nu.marginalia.ping.model.ErrorClassification;
import nu.marginalia.ping.svc.*;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.test.TestMigrationLoader;
import org.apache.hc.client5.http.classic.HttpClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class AvailabilityJobSchedulerTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");


    static HikariDataSource dataSource;
    static HttpClient client;

    @BeforeAll
    public static void setup() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);
        client = new HttpClientProvider().get();

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO EC_DOMAIN (domain_name, domain_top, node_affinity) VALUES ('www.marginalia.nu', 'marginalia.nu', 1)");
        }
    }


    @Test
    @Disabled
    void test() throws Exception {
        var clientProvider = new HttpClientProvider();
        PingDao pingDao = new PingDao(dataSource);
        PingHttpFetcher pingHttpFetcher = new PingHttpFetcher(clientProvider.get());
        ProcessConfiguration processConfig = new ProcessConfiguration("test", 1, UUID.randomUUID());

        Map<ErrorClassification, Duration> initialTimeouts = new HashMap<>();
        Map<ErrorClassification, Duration> maxTimeouts = new HashMap<>();

        for (ErrorClassification classification : ErrorClassification.values()) {
            initialTimeouts.put(classification, Duration.ofSeconds(5));
            maxTimeouts.put(classification, Duration.ofSeconds(5));
        }

        PingIntervalsConfiguration pic = new PingIntervalsConfiguration(
                Duration.ofSeconds(5),
                initialTimeouts,
                maxTimeouts
        );

        DomainDnsInformationFactory dnsDomainInformationFactory = new DomainDnsInformationFactory(processConfig, pic);

        PingJobScheduler pingJobScheduler = new PingJobScheduler(
                new HttpPingService(
                        new LocalDomainCoordinator(),
                        pingHttpFetcher,
                        new DomainAvailabilityInformationFactory(new GeoIpDictionary(), new BackoffStrategy(pic)),
                        new DomainSecurityInformationFactory()),
                new DnsPingService(new PingDnsFetcher(List.of("8.8.8.8", "8.8.4.4")),
                        dnsDomainInformationFactory),
                new LocalDomainCoordinator(),
                pingDao
        );

        TimeUnit.SECONDS.sleep(30);
        System.out.println("Shutting down PingJobScheduler");
        pingJobScheduler.stop();
    }

    @AfterAll
    public static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }

    }
}