package nu.marginalia.nsfw;


import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Named;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Tag("slow")
@Testcontainers
class NsfwDomainFilterTest extends AbstractModule {

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
        tempDir = Files.createTempDirectory(NsfwDomainFilterTest.class.getSimpleName());

        System.setProperty("system.homePath", tempDir.toString());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES (?, ?, 1)")
        ) {

            // Ensure the database is ready
            conn.createStatement().execute("SELECT 1");

            stmt.setString(1, "www.google.com");
            stmt.setString(2, "google.com");
            stmt.executeUpdate();
            stmt.setString(1, "www.bing.com");
            stmt.setString(2, "bing.com");
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to the database", e);
        }
    }

    @Provides
    @Named("nsfw.dangerLists")
    public List<String> nsfwDomainLists1() {
        return List.of(
                "https://downloads.marginalia.nu/test/list1"
        );
    }

    @Provides
    @Named("nsfw.smutLists")
    public List<String> nsfwDomainLists2() {
        return List.of(
                "https://downloads.marginalia.nu/test/list2.gz"
        );
    }

    public void configure() {
        bind(HikariDataSource.class).toInstance(dataSource);
    }

    @Test
    public void test() {
        var filter = Guice
                .createInjector(this)
                .getInstance(NsfwDomainFilter.class);

        filter.fetchLists();

        assertTrue(filter.isBlocked(1, NsfwDomainFilter.NSFW_BLOCK_DANGER));
        assertTrue(filter.isBlocked(1, NsfwDomainFilter.NSFW_BLOCK_SMUT));
        assertFalse(filter.isBlocked(2, NsfwDomainFilter.NSFW_BLOCK_DANGER));
        assertTrue(filter.isBlocked(2, NsfwDomainFilter.NSFW_BLOCK_SMUT));
    }

}