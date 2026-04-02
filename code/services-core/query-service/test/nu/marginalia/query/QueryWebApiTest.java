package nu.marginalia.query;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.nsfw.NsfwFilterModule;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
public class QueryWebApiTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static TestGrpcChannelPoolFactory indexChannelFactory;
    static IndexApiMock indexApiMock = new IndexApiMock();
    static QueryGRPCService queryGRPCService;

    @BeforeAll
    public static void setup() throws IOException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        indexChannelFactory = new TestGrpcChannelPoolFactory(List.of(indexApiMock));

        var injector = Guice.createInjector(new NsfwFilterModule(),
                new AbstractModule() {
            @Override
            protected void configure() {
                bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
                bind(HikariDataSource.class).toInstance(dataSource);
                bind(GrpcChannelPoolFactoryIf.class).toInstance(indexChannelFactory);
            }
        });

        queryGRPCService = injector.getInstance(QueryGRPCService.class);
    }

    @AfterEach
    public void tearDown() {
        indexApiMock.reset();
    }

    @AfterAll
    public static void tearDownAll() {
        indexChannelFactory.close();
    }

    @Test
    public void executeApiQuery__NoFilter() {
        var result = queryGRPCService.executeApiQuery(
                "test query",
                RpcQueryLimits.newBuilder()
                        .setTimeoutMs(150)
                        .setResultsByDomain(2)
                        .setResultsTotal(20)
                        .build(),
                "en",
                NsfwFilterTier.DANGER,
                SearchFilterDefaults.NO_FILTER.asFilterSpec(),
                new IndexClient.Pagination(1, 20)
        );

        assertNotNull(result);
        assertNotNull(result.result());
    }

    @Test
    public void executeApiQuery__NamedFilter() {
        var result = queryGRPCService.executeApiQuery(
                "test query",
                RpcQueryLimits.newBuilder()
                        .setTimeoutMs(150)
                        .setResultsByDomain(5)
                        .setResultsTotal(10)
                        .build(),
                "en",
                NsfwFilterTier.PORN,
                SearchFilterDefaults.SMALLWEB.asFilterSpec(),
                new IndexClient.Pagination(1, 10)
        );

        assertNotNull(result);
        assertNotNull(result.result());
    }

    @Test
    public void executeApiQuery__NsfwOff() {
        var result = queryGRPCService.executeApiQuery(
                "test query",
                RpcQueryLimits.newBuilder()
                        .setTimeoutMs(250)
                        .setResultsByDomain(2)
                        .setResultsTotal(20)
                        .build(),
                "en",
                NsfwFilterTier.OFF,
                SearchFilterDefaults.NO_FILTER.asFilterSpec(),
                new IndexClient.Pagination(1, 20)
        );

        assertNotNull(result);
        assertNotNull(result.result());
    }

}
