package nu.marginalia.api;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.test.MockContext;
import io.jooby.test.MockRouter;
import nu.marginalia.api.polar.PolarBenefit;
import nu.marginalia.api.polar.PolarBenefits;
import nu.marginalia.api.polar.PolarClient;
import nu.marginalia.api.searchquery.*;
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
import java.util.concurrent.TimeoutException;


@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
public class ApiV2Test {
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
    static DomainInfoApiMock domainInfoApiMock = new DomainInfoApiMock();

    @BeforeAll
    public static void setup() throws SQLException, TimeoutException, IOException, InterruptedException {
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

        testGrpcChannelPoolFactory = new TestGrpcChannelPoolFactory(List.of(queryApiMock, domainInfoApiMock));

        ApiV2 apiV2 = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(HikariDataSource.class).toInstance(dataSource);
                bind(GrpcChannelPoolFactoryIf.class).toInstance(testGrpcChannelPoolFactory);
                bind(PolarClient.class).toInstance(PolarClient.asDisabled());
                bind(PolarBenefits.class).toInstance(PolarBenefits.asDisabled());
            }
        }).getInstance(ApiV2.class);

        jooby = new Jooby();

        jooby.install(apiV2);

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
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");

        router.get("/api/v2/key", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
        });
    }

    @Test
    public void testGetLicenseApi__bad_key() {
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "invalid");

        router.get("/api/v2/key", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.UNAUTHORIZED, rsp.getStatusCode());
        });
    }

    @Test
    public void testSearch() {
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        context.setQueryString("query=test");

        router.get("/api/v2/search", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            System.out.println(rsp.getHeaders());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
            Assertions.assertEquals(1, queryApiMock.getSentQueries().size());
        });

        router.get("/api/v2/search", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            System.out.println(rsp.getHeaders());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
            Assertions.assertEquals(1, queryApiMock.getSentQueries().size());
        });
    }

    @Test
    public void testFilters__list__empty() {
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");

        router.get("/api/v2/filter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
        });
    }

    @Test
    public void testFilters__list__values() {
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        context.setBody("""
                <?xml version="1.0"?>
                <filter>
                <search-set>NONE</search-set>
                </filter>
                """);

        router.post("/api/v2/filter/myfilter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.ACCEPTED, rsp.getStatusCode());
        });

        context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        router.get("/api/v2/filter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
            Assertions.assertEquals("[\"myfilter\"]", rsp.value());
        });
    }

    @Test
    public void testFilters__delete() {
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        context.setBody("""
                <?xml version="1.0"?>
                <filter>
                <search-set>NONE</search-set>
                </filter>
                """);

        router.post("/api/v2/filter/myfilter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.ACCEPTED, rsp.getStatusCode());
        });

        context = new MockContext();
        context.setRequestHeader("API-Key", "test");

        router.delete("/api/v2/filter/myfilter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.ACCEPTED, rsp.getStatusCode());
        });

        context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        router.get("/api/v2/filter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());

            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
            Assertions.assertEquals("[]", rsp.value());
        });
    }


    @Test
    public void testSiteInfo__known_domain() throws SQLException {
        // Insert a domain into the database so the lookup succeeds
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT IGNORE INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP) VALUES ('example.com', 'example.com')");
        }

        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        context.setRequestPath("/api/v2/site/example.com");

        router.get("/api/v2/site/example.com", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());

            String body = rsp.value().toString();
            Assertions.assertTrue(body.contains("\"domain\":\"example.com\""));
            Assertions.assertTrue(body.contains("\"state\":\"ACTIVE\""));
            Assertions.assertTrue(body.contains("\"serverAvailable\":true"));
            Assertions.assertTrue(body.contains("\"sslVersion\":\"TLSv1.3\""));
        });
    }

    @Test
    public void testSiteInfo__unknown_domain() {
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");

        router.get("/api/v2/site/nonexistent.example.com", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            Assertions.assertEquals(StatusCode.NOT_FOUND, rsp.getStatusCode());
        });
    }

    @Test
    public void testSiteInfo__no_api_key() {
        MockContext context = new MockContext();

        router.get("/api/v2/site/example.com", context, rsp -> {
            Assertions.assertEquals(StatusCode.BAD_REQUEST, rsp.getStatusCode());
        });
    }

    @Test
    public void testSimilarDomains() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT IGNORE INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP) VALUES ('example.com', 'example.com')");
        }

        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");

        router.get("/api/v2/site/example.com/similar", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());

            String body = rsp.value().toString();
            Assertions.assertTrue(body.contains("\"domain\":\"example.com\""));
            Assertions.assertTrue(body.contains("\"similar-site.org\""));
            Assertions.assertTrue(body.contains("\"BIDIRECTIONAL\""));
        });
    }

    @Test
    public void testLinkingDomains() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT IGNORE INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP) VALUES ('example.com', 'example.com')");
        }

        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");

        router.get("/api/v2/site/example.com/linking", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());

            String body = rsp.value().toString();
            Assertions.assertTrue(body.contains("\"domain\":\"example.com\""));
            Assertions.assertTrue(body.contains("\"linking-site.net\""));
            Assertions.assertTrue(body.contains("\"BACKWARD\""));
        });
    }

    @Test
    public void testSimilarDomains__unknown_domain() {
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");

        router.get("/api/v2/site/nonexistent.example.com/similar", context, rsp -> {
            Assertions.assertEquals(StatusCode.NOT_FOUND, rsp.getStatusCode());
        });
    }

    @Test
    public void testFilters__update() {
        MockContext context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        context.setBody("""
                <?xml version="1.0"?>
                <filter>
                <search-set>NONE</search-set>
                </filter>
                """);

        router.post("/api/v2/filter/myfilter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.ACCEPTED, rsp.getStatusCode());
        });

        context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        context.setBody("""
                <?xml version="1.0"?>
                <filter>
                <search-set>BLOGS</search-set>
                </filter>
                """);

        router.post("/api/v2/filter/myfilter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());
            Assertions.assertEquals(StatusCode.ACCEPTED, rsp.getStatusCode());
        });

        context = new MockContext();
        context.setRequestHeader("API-Key", "test");
        router.get("/api/v2/filter/myfilter", context, rsp -> {
            System.out.println(rsp.getStatusCode());
            System.out.println(rsp.getContentType());
            System.out.println(rsp.value());

            Assertions.assertEquals(StatusCode.OK, rsp.getStatusCode());
        });
    }
}