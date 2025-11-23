package nu.marginalia.api;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.test.MockContext;
import io.jooby.test.MockRouter;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.svc.LicenseService;
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
import java.util.ArrayList;
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

        testGrpcChannelPoolFactory = new TestGrpcChannelPoolFactory(List.of(queryApiMock));

        ApiV2 apiV2 = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(HikariDataSource.class).toInstance(dataSource);
                bind(GrpcChannelPoolFactoryIf.class).toInstance(testGrpcChannelPoolFactory);
            }
        }).getInstance(ApiV2.class);

        jooby = new Jooby();

        apiV2.registerApi(jooby);

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