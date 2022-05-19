package nu.marginalia.wmsa.edge;

import com.zaxxer.hikari.HikariDataSource;
import io.reactivex.rxjava3.functions.Consumer;
import lombok.SneakyThrows;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlFeature;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.data.dao.task.*;
import nu.marginalia.wmsa.edge.director.EdgeDirectorService;
import nu.marginalia.wmsa.edge.director.client.EdgeDirectorClient;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;
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
import java.util.stream.Stream;

import static nu.marginalia.util.TestUtil.evalScript;
import static nu.marginalia.util.TestUtil.getConnection;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class EdgeDirectorServiceTest {
    static EdgeDirectorService service;
    static EdgeDirectorClient client;

    private static HikariDataSource dataSource;

    static final int testPort = TestUtil.getPort();
    private static EdgeDataStoreTaskDaoImpl taskDao;
    private static EdgeDataStoreDaoImpl dataDao;

    private static Initialization init;

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

        client = new EdgeDirectorClient();
        client.setServiceRoute("127.0.0.1", testPort);

        dataDao = new EdgeDataStoreDaoImpl(dataSource);
        var ongoingJobs = new EdgeDataStoreTaskOngoingJobs();
        init = new Initialization();
        taskDao = new EdgeDataStoreTaskDaoImpl(dataSource, new EdgeDomainBlacklistImpl(dataSource),
                new EdgeDataStoreTaskTuner(dataSource), ongoingJobs, new EdgeFinishTasksQueue(dataSource, ongoingJobs), init);
        service = new EdgeDirectorService("127.0.0.1",
                testPort,
                init,
                taskDao, null
                );

        Spark.awaitInitialization();
    }

    @SneakyThrows
    @BeforeEach
    public void clearDb() {
        taskDao.clearCaches();

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
        init.setReady();
    }

    @SneakyThrows
    @AfterAll
    public static void tearDownAll() {
        dataSource.close();
        Spark.awaitStop();
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
    public void testEdgeGetIndexTask() throws URISyntaxException, InterruptedException {
        dataDao.putUrl(-2,
                new EdgeUrl("https://marginalia.nu/"),
                new EdgeUrl("https://marginalia.nu/a"),
                new EdgeUrl("https://marginalia.nu/b"),
                new EdgeUrl("https://marginalia.nu/c"));

        dataDao.putUrl(-1.5,
                new EdgeUrl("https://www.marginalia.nu/"),
                new EdgeUrl("https://www.marginalia.nu/a"),
                new EdgeUrl("https://www.marginalia.nu/b"),
                new EdgeUrl("https://www.marginalia.nu/c"));

        dataDao.putUrl(-2.5,
                new EdgeUrl("https://memex.marginalia.nu/"),
                new EdgeUrl("https://memex.marginalia.nu/a"),
                new EdgeUrl("https://memex.marginalia.nu/b"),
                new EdgeUrl("https://memex.marginalia.nu/c"));

        Thread.sleep(1000);

        for (int i = 0; i < 4; i++) {
            var rsp = client.getDiscoverTask(Context.internal()).blockingFirst();
            System.out.println(rsp);
            if (rsp.domain != null) {
                client.finishTask(Context.internal(), rsp.domain, -2, EdgeDomainIndexingState.ACTIVE).blockingSubscribe();
                Thread.sleep(1000);
            }
        }

        for (int i = 0; i < 4; i++) {
            var rsp = client.getIndexTask(Context.internal(), 2, 10).blockingFirst();
            System.out.println(rsp);
        }
    }


    @Test
    public void testEdgeGetDiscoverTask() throws URISyntaxException {

        update("UPDATE EC_DOMAIN SET INDEXED=0");
        dataDao.putUrl(-2,
                new EdgeUrl("https://marginalia.nu/"),
                new EdgeUrl("https://marginalia.nu/a"),
                new EdgeUrl("https://marginalia.nu/b"),
                new EdgeUrl("https://marginalia.nu/c"));


        query("SELECT URL,VISITED FROM EC_URL WHERE DOMAIN_ID=1", (rsp) -> {
            while (rsp.next()) {
                System.out.println(rsp.getString(1) + " - " + rsp.getString(2));
            }
        });
        dataDao.putUrlVisited(new EdgeUrlVisit(new EdgeUrl("https://marginalia.nu/c"),
                0xF34, -1.1, "title", "desc",
                "ip", "test", HtmlFeature.AFFILIATE_LINK.bit,123, 456,
                EdgeUrlState.OK));

        query("SELECT URL,VISITED FROM EC_URL WHERE DOMAIN_ID=1", (rsp) -> {
            while (rsp.next()) {
                System.out.println(rsp.getString(1) + " - " + rsp.getString(2));
            }
        });

        dataDao.putUrl(-2.,
                new EdgeUrl("https://www.marginalia.nu/"),
                new EdgeUrl("https://www.marginalia.nu/y"));

        query("SELECT URL_PART, INDEXED FROM EC_DOMAIN", (rsp) -> {
            while (rsp.next()) {
                System.out.println(rsp.getString(1) + " - " + rsp.getString(2));
            }
        });

        {
            var task = client.getDiscoverTask(Context.internal()).blockingFirst();
            System.out.println(
                    task
            );
            assertEquals(3, task.urls.size());
            task.urls.forEach(System.out::println);
        }

        {
            var task = client.getDiscoverTask(Context.internal()).blockingFirst();
            assertEquals(2, task.urls.size());
            task.urls.forEach(System.out::println);
        }

        {
            var task = client.getDiscoverTask(Context.internal()).blockingFirst();
            assertEquals(0, task.urls.size());
            task.urls.forEach(System.out::println);
        }
    }


    @Test
    public void testFinalizeTask() throws URISyntaxException {
        Stream.of(new EdgeUrl("https://marginalia.nu/"),
                new EdgeUrl("https://marginalia.nu/q"),
                new EdgeUrl("https://marginalia.nu/r"))
                .forEach(data -> dataDao.putUrl(-2, data));

        update("UPDATE EC_DOMAIN SET INDEXED=1");

        {
            var task = client.getIndexTask(Context.internal(), 1, 10).blockingFirst();
            assertEquals(3, task.urls.size());
            task.urls.forEach(System.out::println);
        }
        client.finishTask(Context.internal(), new EdgeDomain("https://marginalia.nu"), -5, EdgeDomainIndexingState.ACTIVE)
                .blockingSubscribe();
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