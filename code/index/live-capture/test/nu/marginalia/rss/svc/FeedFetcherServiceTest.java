package nu.marginalia.rss.svc;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.name.Names;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.coordination.LocalDomainCoordinator;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Tag("slow")
@Testcontainers
class FeedFetcherServiceTest extends AbstractModule {
    FeedFetcherService feedFetcherService;
    FeedDb feedDb;

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static Path tempDir;

    @BeforeAll
    public static void setUpDb() throws IOException {
        tempDir = Files.createTempDirectory(FeedFetcherServiceTest.class.getSimpleName());

        System.setProperty("system.homePath", tempDir.toString());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);
    }

    @BeforeEach
    public void setUp() throws IOException {
        if (!Files.exists(tempDir)) {
            Files.createDirectory(tempDir);
        }
        // Trick WmsaHome that this is a full home directory
        if (!Files.exists(tempDir.resolve("model"))) {
            Files.createDirectory(tempDir.resolve("model"));
        }
        if (!Files.exists(tempDir.resolve("data"))) {
            Files.createDirectory(tempDir.resolve("data"));
        }
        var injector = Guice.createInjector(this);

        feedDb = injector.getInstance(FeedDb.class);
        feedFetcherService = injector.getInstance(FeedFetcherService.class);

    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    public void configure() {
        bind(DomainCoordinator.class).to(LocalDomainCoordinator.class);
        bind(HikariDataSource.class).toInstance(dataSource);
        bind(ServiceRegistryIf.class).toInstance(Mockito.mock(ServiceRegistryIf.class));
        bind(ServiceConfiguration.class).toInstance(new ServiceConfiguration(ServiceId.Index, 1, "", "", 0, UUID.randomUUID()));
        bind(Integer.class).annotatedWith(Names.named("wmsa-system-node")).toInstance(1);
    }

    @Tag("flaky")
    @Test
    public void testSunnyDay() throws Exception {
        try (var writer = feedDb.createWriter()) {
            writer.saveFeed(new FeedItems("www.marginalia.nu", "https://www.marginalia.nu/log/index.xml", "", List.of()));
            feedDb.switchDb(writer);
        }

        feedFetcherService.updateFeeds(FeedFetcherService.UpdateMode.REFRESH);

        var result = feedDb.getFeed(new EdgeDomain("www.marginalia.nu"));
        System.out.println(result);
        Assertions.assertFalse(result.isEmpty());
    }

    @Tag("flaky")
    @Test
    public void testFetchRepeatedly() throws Exception {
        try (var writer = feedDb.createWriter()) {
            writer.saveFeed(new FeedItems("www.marginalia.nu", "https://www.marginalia.nu/log/index.xml", "", List.of()));
            feedDb.switchDb(writer);
        }

        feedFetcherService.updateFeeds(FeedFetcherService.UpdateMode.REFRESH);
        Assertions.assertNotNull(feedDb.getEtag(new EdgeDomain("www.marginalia.nu")));
        feedFetcherService.updateFeeds(FeedFetcherService.UpdateMode.REFRESH);
        Assertions.assertNotNull(feedDb.getEtag(new EdgeDomain("www.marginalia.nu")));
        feedFetcherService.updateFeeds(FeedFetcherService.UpdateMode.REFRESH);
        Assertions.assertNotNull(feedDb.getEtag(new EdgeDomain("www.marginalia.nu")));

        var result = feedDb.getFeed(new EdgeDomain("www.marginalia.nu"));
        System.out.println(result);
        Assertions.assertFalse(result.isEmpty());
    }

    @Tag("flaky")
    @Test
    public void test404() throws Exception {
        try (var writer = feedDb.createWriter()) {
            writer.saveFeed(new FeedItems("www.marginalia.nu", "https://www.marginalia.nu/log/missing.xml", "", List.of()));
            feedDb.switchDb(writer);
        }

        feedFetcherService.updateFeeds(FeedFetcherService.UpdateMode.REFRESH);

        // We forget the feed on a 404 error
        Assertions.assertEquals(FeedItems.none(), feedDb.getFeed(new EdgeDomain("www.marginalia.nu")));
    }

}