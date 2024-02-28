package nu.marginalia.api.svc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import spark.HaltException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
@Testcontainers
class LicenseServiceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    private static LicenseService service;
    private static HikariDataSource dataSource;

    @BeforeAll
    public static void setUp() throws SQLException {
        mariaDBContainer.start();

        dataSource = getConnection(mariaDBContainer.getJdbcUrl());
        TestMigrationLoader.flywayMigration(dataSource);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO EC_API_KEY(LICENSE_KEY, LICENSE, NAME, EMAIL, RATE)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {

            stmt.setString(1, "public");
            stmt.setString(2, "Public Domain");
            stmt.setString(3, "John Q. Public");
            stmt.setString(4, "info@example.com");
            stmt.setInt(5, 0);

            stmt.addBatch();

            stmt.setString(1, "limited");
            stmt.setString(2, "CC BY NC SA 4.0");
            stmt.setString(3, "Contact Info");
            stmt.setString(4, "about@example.com");
            stmt.setInt(5, 30);

            stmt.addBatch();
            stmt.executeBatch();
        }

        service = new LicenseService(dataSource);
    }

    @AfterAll
    public static void tearDown() {
        dataSource.close();
    }

    @Test
    void testLicense() {
        var publicLicense = service.getLicense("public");
        var limitedLicense = service.getLicense("limited");

        assertEquals(publicLicense.rate, 0);
        assertEquals(publicLicense.key, "public");
        assertEquals(publicLicense.license, "Public Domain");
        assertEquals(publicLicense.name, "John Q. Public");

        assertEquals(limitedLicense.rate, 30);
        assertEquals(limitedLicense.key, "limited");
        assertEquals(limitedLicense.license, "CC BY NC SA 4.0");
        assertEquals(limitedLicense.name, "Contact Info");

    }

    @Test
    void testLicenseCache() {
        var publicLicense = service.getLicense("public");
        var publicLicenseAgain = service.getLicense("public");

        Assertions.assertSame(publicLicense, publicLicenseAgain);
    }

    @Test
    void testUnknownLiecense() {
        assertHaltsWithErrorCode(401, () -> service.getLicense("invalid code"));
    }

    @Test
    public void testBadKey() {
        assertHaltsWithErrorCode(400, () -> service.getLicense(""));
        assertHaltsWithErrorCode(400, () -> service.getLicense(null));
    }

    public void assertHaltsWithErrorCode(int expectedCode, Runnable runnable) {
        try {
            runnable.run();

            Assertions.fail("Expected HaltException with status code " + expectedCode + " but no exception was thrown.");
        } catch (HaltException e) {
            assertEquals(expectedCode, e.statusCode(), "Expected HaltException with status code " + expectedCode + " but got " + e.statusCode() + " instead.");
        }

    }

    @SneakyThrows
    public static HikariDataSource getConnection(String connString) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connString);
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        return new HikariDataSource(config);
    }
}