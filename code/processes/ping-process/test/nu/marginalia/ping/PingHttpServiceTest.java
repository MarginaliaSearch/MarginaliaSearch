package nu.marginalia.ping;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.coordination.LocalDomainCoordinator;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.ping.fetcher.PingHttpFetcher;
import nu.marginalia.ping.io.HttpClientProvider;
import nu.marginalia.ping.model.DomainReference;
import nu.marginalia.ping.svc.DomainAvailabilityInformationFactory;
import nu.marginalia.ping.svc.DomainSecurityInformationFactory;
import nu.marginalia.ping.svc.HttpPingService;
import nu.marginalia.test.TestMigrationLoader;
import org.apache.hc.client5.http.classic.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class PingHttpServiceTest {
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
    }

    @AfterAll
    public static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }

    }

    @Tag("flaky") // Do not run this test in CI
    @Test
    public void testGetSslInfo() throws Exception {
        var provider = new HttpClientProvider();
        var pingService = new HttpPingService(
                new LocalDomainCoordinator(),
                new PingHttpFetcher(provider.get()),
                new DomainAvailabilityInformationFactory(new GeoIpDictionary(),
                        new BackoffStrategy(PingModule.createPingIntervalsConfiguration())
                        ),
                new DomainSecurityInformationFactory());

        var output = pingService.pingDomain(new DomainReference(1, 1, "www.marginalia.nu"), null, null);
        for (var model : output) {
            System.out.println(model);
        }
    }
}
