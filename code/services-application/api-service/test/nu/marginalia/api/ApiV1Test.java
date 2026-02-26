package nu.marginalia.api;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.test.MockRouter;
import nu.marginalia.api.polar.PolarBenefits;
import nu.marginalia.api.polar.PolarClient;
import nu.marginalia.service.client.GrpcChannelPoolFactoryIf;
import nu.marginalia.service.client.TestGrpcChannelPoolFactory;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

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
    static Jooby jooby;
    static MockRouter router;
    static TestGrpcChannelPoolFactory testGrpcChannelPoolFactory;
    static QueryApiMock queryApiMock = new QueryApiMock();

    @BeforeAll
    public static void setup() throws SQLException, IOException {
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

        testGrpcChannelPoolFactory = new TestGrpcChannelPoolFactory(List.of(queryApiMock));

        ApiV1 apiV1 = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(HikariDataSource.class).toInstance(dataSource);
                bind(GrpcChannelPoolFactoryIf.class).toInstance(testGrpcChannelPoolFactory);
                bind(PolarClient.class).toInstance(PolarClient.asDisabled());
                bind(PolarBenefits.class).toInstance(PolarBenefits.asDisabled());
            }
        }).getInstance(ApiV1.class);

        jooby = new Jooby();

        apiV1.registerApi(jooby);

        router = new MockRouter(jooby);
        router.setFullExecution(true);
    }

    @AfterEach
    public void shutDown() throws SQLException {
        queryApiMock.reset();
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeQuery("TRUNCATE TABLE SEARCH_FILTER");
        }
    }

    @AfterAll
    public static void shutDownAll() {
        testGrpcChannelPoolFactory.close();
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
            Assertions.assertEquals(1, queryApiMock.getSentQueries().size());
            Assertions.assertEquals("query", queryApiMock.getSentQueries().getFirst().getHumanQuery());
        });
    }

}