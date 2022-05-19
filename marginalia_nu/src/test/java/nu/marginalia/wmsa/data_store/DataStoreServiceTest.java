package nu.marginalia.wmsa.data_store;

import com.zaxxer.hikari.HikariDataSource;
import io.reactivex.rxjava3.functions.Consumer;
import lombok.SneakyThrows;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.data_store.client.DataStoreClient;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.model.*;
import org.eclipse.jetty.util.UrlEncoded;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import spark.Spark;

import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.util.TestUtil.evalScript;
import static nu.marginalia.util.TestUtil.getConnection;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class DataStoreServiceTest {
    static DataStoreService service;
    static DataStoreClient client;

    private static HikariDataSource dataSource;
    private static EdgeDataStoreService edgeService;

    static final int testPort = TestUtil.getPort();
    private static EdgeDataStoreDaoImpl edgeDataStore;

    @SneakyThrows
    public static HikariDataSource provideConnection() {
        return getConnection();
    }


    @BeforeAll
    public static void setUpClass() {
        Spark.port(testPort);
        System.setProperty("service-name", "test");

        dataSource = provideConnection();
        dataSource.setKeepaliveTime(100);
        dataSource.setIdleTimeout(100);

        client = new DataStoreClient();
        client.setServiceRoute("127.0.0.1", testPort);

        edgeDataStore = new EdgeDataStoreDaoImpl(dataSource);
        edgeService = new EdgeDataStoreService(edgeDataStore);
        service = new DataStoreService("127.0.0.1",
                testPort,
                new FileRepository(),
                dataSource,
                edgeService,
                new Initialization(), null
                );

        Spark.awaitInitialization();
    }

    @SneakyThrows
    @BeforeEach
    public void clearDb() {
        edgeDataStore.clearCaches();

        evalScript(dataSource, "sql/data-store-init.sql");
        evalScript(dataSource, "sql/edge-crawler-cache.sql");

        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.createStatement()) {
                Assertions.assertTrue(stmt.executeUpdate("DELETE FROM EC_URL") >= 0);
                Assertions.assertTrue(stmt.executeUpdate("DELETE FROM EC_DOMAIN_LINK") >= 0);
                Assertions.assertTrue(stmt.executeUpdate("DELETE FROM EC_DOMAIN") >= 0);
            }
            connection.commit();
        }
    }

    @SneakyThrows
    @AfterAll
    public static void tearDownAll() {
        dataSource.close();
        Spark.awaitStop();
    }

    @Test
    public void test() {
        client.offerJson(Context.internal(), String.class, "Hello World", "test", "aaa").blockingSubscribe();
        assertEquals("Hello World",
                client.getJson(Context.internal(), String.class, "test", "aaa").blockingFirst());
    }

    @Test
    public void testUnderscore() {
        client.offerJson(Context.internal(), String.class, "Hello World", "test", "aaa_bbb").blockingSubscribe();
        assertEquals("Hello World",
                client.getJson(Context.internal(), String.class, "test", "aaa_bbb").blockingFirst());
    }

    @Test
    public void testList() {
        client.offerJson(Context.internal(), String.class, "Hello", "test", "aaa").blockingSubscribe();
        client.offerJson(Context.internal(), String.class, "World", "test", "bbb").blockingSubscribe();
        client.offerJson(Context.internal(), String.class, "Dude", "dummy", "ccc").blockingSubscribe();

        List<String> allElements = new ArrayList<>();
        client.getJsonIndicies(Context.internal(), String.class, "test")
                .flatMapIterable(i->i)
                .concatMap(id -> client.getJson(Context.internal(), String.class, "test", id))
                .blockingForEach(allElements::add);

        assertEquals(2, allElements.size());
        assertTrue(allElements.contains("Hello"));
        assertTrue(allElements.contains("World"));
    }


    @Test
    public void testEdgePutUrl() throws URISyntaxException {
        client.putUrl(Context.internal(), -2, new EdgeUrl("https://marginalia.nu/"))
                .blockingSubscribe();
    }

    @SneakyThrows
    void query(String query, Consumer<ResultSet> resultConsumer) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()
        ) {
            resultConsumer.accept(stmt.executeQuery(query));

        } catch (SQLException throwables) {
            Assertions.fail(throwables);
        }
    }

    @SneakyThrows
    void update(String sql) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()
        ) {
            Assertions.assertTrue(stmt.executeUpdate(sql) >= 0);
            conn.commit();
        } catch (SQLException throwables) {
            Assertions.fail(throwables);

        }
    }

    @Test
    public void test2() throws URISyntaxException {
        var request = new EdgeUrl("https://marginalia.nu/");
        var domain = UrlEncoded.encodeString(request.domain.toString());
        var path  = UrlEncoded.encodeString(request.path);

        System.out.println(domain);
        System.out.println(path);
    }

}