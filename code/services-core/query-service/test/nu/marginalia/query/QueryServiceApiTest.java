package nu.marginalia.query;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.nsfw.NsfwFilterModule;
import nu.marginalia.service.client.GrpcChannelPoolFactoryIf;
import nu.marginalia.service.client.TestGrpcChannelPoolFactory;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
public class QueryServiceApiTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static TestGrpcChannelPoolFactory indexChannelFactory;
    static TestGrpcChannelPoolFactory queryChannelFactory;
    static IndexApiMock indexApiMock = new IndexApiMock();

    static QueryClient queryClient;
    @BeforeAll
    public static void setup() throws IOException, InterruptedException {
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

        queryChannelFactory = new TestGrpcChannelPoolFactory(List.of(injector.getInstance(QueryGRPCService.class)));
        queryClient = new QueryClient(queryChannelFactory, new Initialization());
    }

    @AfterEach
    public void tearDown() {
        indexApiMock.reset();
    }

    @AfterAll
    public static void tearDownAll() {
        indexChannelFactory.close();
        queryChannelFactory.close();
    }

    @Test
    public void test() throws TimeoutException {
        var rs = queryClient.search(
                new QueryFilterSpec.FilterByName("SYSTEM", "NO_FILTER"),
                "test",
                "en",
                NsfwFilterTier.DANGER,
                RpcQueryLimits.newBuilder()
                        .setTimeoutMs(150)
                        .setResultsByDomain(100)
                        .setResultsTotal(100)
                        .build(), 1);

    }

    @Test
    public void testTimeout() {
        indexApiMock.setHandler((req, obs) ->{
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            obs.onCompleted();
        });

        Instant start = Instant.now();

        try {
            queryClient.search(
                    new QueryFilterSpec.FilterByName("SYSTEM", "NO_FILTER"),
                    "test",
                    "en",
                    NsfwFilterTier.DANGER,
                    RpcQueryLimits.newBuilder()
                            .setTimeoutMs(150)
                            .setResultsByDomain(100)
                            .setResultsTotal(100)
                            .build(), 1);
            Assertions.fail("No TimeoutException");
        }
        catch (TimeoutException ex) {
            //
        }
        Instant end = Instant.now();
        Assertions.assertTrue(Duration.between(end, start).compareTo(Duration.ofMillis(450)) < 0);
    }
}
