package nu.marginalia.api;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.test.MockRouter;
import nu.marginalia.api.model.ApiSearchResults;
import nu.marginalia.api.svc.LicenseService;
import nu.marginalia.api.svc.RateLimiterService;
import nu.marginalia.api.svc.ResponseCache;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.anyInt;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
public class ApiV1Test {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static LicenseService licenseService;
    static Jooby jooby;
    static MockRouter router;

    @BeforeAll
    public static void setup() throws SQLException, TimeoutException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeQuery("""
                    INSERT INTO EC_API_KEY(LICENSE_KEY, LICENSE, NAME, EMAIL, RATE)
                    VALUES ("TEST", "TEST-LICENSE", "TEST-NAME", "TEST@TEST", 90)
                    """);
        }

        licenseService = new LicenseService(dataSource);

        ApiSearchOperator operatorMock = Mockito.mock(ApiSearchOperator.class);
        Mockito.when(operatorMock.query(ArgumentMatchers.any(), anyInt(), anyInt(), anyInt(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ApiSearchResults("test", "test", new ArrayList<>()));
        jooby = new Jooby() {
            {
                new ApiV1(
                        new ResponseCache(),
                        licenseService,
                        new RateLimiterService(),
                        operatorMock
                ).registerApi(this);
            }
        };

        router = new MockRouter(jooby);

    }

    @Test
    public void testGetLicenseApi__good_key() {
        router.get("/api/v1/test", rsp->{
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
        });
    }

    @Test
    public void testGetLicenseApi__bad_key() {
        router.get("/api/v1/invalid", rsp->{
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.UNAUTHORIZED, rsp.getStatusCode());
        });
    }

    @Test
    public void testSearch() {
        router.get("/api/v1/test/search/query", rsp->{
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
        });
    }

}