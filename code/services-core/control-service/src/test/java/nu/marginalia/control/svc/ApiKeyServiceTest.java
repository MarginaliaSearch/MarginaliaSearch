package nu.marginalia.control.svc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.app.model.ApiKeyModel;
import nu.marginalia.control.app.svc.ApiKeyService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Testcontainers
@Execution(SAME_THREAD)
@Tag("slow")
public class ApiKeyServiceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("db/migration/V23_06_0_006__api_key.sql")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
    }

    @AfterAll
    public static void tearDown() {
        dataSource.close();
        mariaDBContainer.close();
    }

    @AfterEach
    public void cleanDb() {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE EC_API_KEY");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getKeys() {
        var apiKeyService = new ApiKeyService(dataSource, null);
        apiKeyService.addApiKey("public domain", "bob dobbs", "bob@dobbstown.com", 30);
        apiKeyService.addApiKey("public domain", "connie dobbs", "cdobbs@dobbstown.com", 15);

        var keys = apiKeyService.getApiKeys();
        System.out.println(keys);
        assertEquals(2, keys.size());
    }

    @Test
    void addApiKey() {
        var apiKeyService = new ApiKeyService(dataSource, null);
        apiKeyService.addApiKey("public domain", "bob dobbs", "bob@dobbstown.com", 30);

        var keys = apiKeyService.getApiKeys();

        System.out.println(keys);
        assertEquals(1, keys.size());

        var key = keys.get(0);

        assertEquals("public domain", key.license());
        assertEquals("bob dobbs", key.name());
        assertEquals("bob@dobbstown.com", key.email());
        assertEquals(30, key.rate());
        assertNotNull(key.licenseKey());
    }

    @Test
    void deleteApiKey() {
        var apiKeyService = new ApiKeyService(dataSource, null);
        apiKeyService.addApiKey("public domain", "bob dobbs", "bob@dobbstown.com", 30);

        List<ApiKeyModel> keys = apiKeyService.getApiKeys();

        assertEquals(1, keys.size());

        String licenseKey=  keys.get(0).licenseKey();
        apiKeyService.deleteApiKey(licenseKey);

        keys = apiKeyService.getApiKeys();
        assertEquals(0, keys.size());
    }
}