package nu.marginalia.wmsa.edge.data;

import com.zaxxer.hikari.HikariDataSource;
import io.reactivex.rxjava3.functions.Consumer;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.data.dao.task.*;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;

import static nu.marginalia.util.TestUtil.evalScript;
import static nu.marginalia.util.TestUtil.getConnection;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class EdgeDataStoreDaoTest {
    HikariDataSource dataSource;
    private EdgeDataStoreTaskDaoImpl taskDao;


    @SneakyThrows
    public static HikariDataSource provideConnection() {
        var conn = getConnection();

        evalScript(conn, "sql/edge-crawler-cache.sql");

        return conn;
    }


    @SneakyThrows
    void query(String query, Consumer<ResultSet> resultConsumer) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()
        ) {
            resultConsumer.accept(stmt.executeQuery(query));

        } catch (Throwable throwables) {
            Assertions.fail(throwables);
        }
    }
    @SneakyThrows
    void update(String sql) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()
        ) {
            stmt.executeUpdate(sql);
            conn.commit();

        } catch (Throwable throwables) {
            Assertions.fail(throwables);

        }
    }

    @SneakyThrows
    @AfterEach
    public void tearDownDb() {
        dataSource.close();
    }


    @SneakyThrows
    @BeforeEach
    public void setUpDb() {
        dataSource = provideConnection();
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.createStatement()) {
                stmt.execute("DELETE FROM EC_URL");
                stmt.execute("DELETE FROM EC_DOMAIN_LINK");
                stmt.execute("DELETE FROM EC_DOMAIN");
                stmt.execute("DELETE FROM EC_URL_DETAILS");
            }
            connection.commit();
        }
        var ongoingJobs = new EdgeDataStoreTaskOngoingJobs();
        Initialization init = new Initialization();
        taskDao = new EdgeDataStoreTaskDaoImpl(dataSource,
                new EdgeDomainBlacklistImpl(dataSource),
                new EdgeDataStoreTaskTuner(dataSource),
                ongoingJobs,
                new EdgeFinishTasksQueue(dataSource, ongoingJobs),
                init);
    }

    @SneakyThrows
    @Test
    public void test() {
        try (var connection = dataSource.getConnection()) {
            var ds = new EdgeDataStoreDaoImpl(dataSource);
            assertFalse(ds.isBlacklisted(new EdgeDomain("https://www.marginalia.nu")));
        }
    }

    @Test
    void putLink() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            var ds = new EdgeDataStoreDaoImpl(dataSource);
            ds.putLink(
                    false, new EdgeDomainLink(new EdgeDomain("https://www.marginalia.nu"),
                            new EdgeDomain("https://www.marginalia.nu")
                    ));
            var res = connection.createStatement().executeQuery("SELECT * FROM EC_DOMAIN_LINK");
            res.next();
            assertEquals(res.getString(1), res.getString(2));
        }
    }

    @SneakyThrows
    @Test
    void putUrl() {
        var ds = new EdgeDataStoreDaoImpl(dataSource);
        ds.putUrl(-2, new EdgeUrl("https://www.marginalia.nu/"));
        ds.putUrl(-2, new EdgeUrl("https://www.marginalia.nu/robots.txt"));
        ds.putUrl(-2, new EdgeUrl("https://www.marginalia.nu/sitemap.xml"));
        ds.putUrl(-2, new EdgeUrl("https://marginalia.nu/"));
        ds.putUrl(-2, new EdgeUrl("https://marginalia.nu/robots.txt"));
        ds.putUrl(-2, new EdgeUrl("https://marginalia.nu/sitemap.xml"));

        taskDao.getIndexTask(0, 100).urls.forEach(System.out::println);
        taskDao.finishIndexTask(new EdgeDomain("https://www.marginalia.nu/"), 0.5, EdgeDomainIndexingState.ACTIVE);
        System.out.println("-");
        taskDao.getIndexTask(0, 100).urls.forEach(System.out::println);
    }


    @SneakyThrows
    @Test
    void putUrlVisit() {
        var ds = new EdgeDataStoreDaoImpl(dataSource);

        var url = new EdgeUrl("https://www.marginalia.nu/");
        ds.putUrl(-2, url);
        ds.putUrlVisited(new EdgeUrlVisit(url, 255, -2., "Bob's Website", "A homepage", "", "test", 0,0, 0, EdgeUrlState.OK));
        var deets = ds.getUrlDetails(ds.getUrlId(url));
        assertEquals(-2., deets.urlQuality);
        assertEquals("Bob's Website", deets.title);
        assertEquals("A homepage", deets.description);
        System.out.println(deets);
    }

    @Test
    void getDomainId() throws URISyntaxException {
        var ds = new EdgeDataStoreDaoImpl(dataSource);
        var domain = new EdgeDomain("www.marginalia.nu");
        var url = new EdgeUrl("https://www.marginalia.nu/");

        ds.putUrl(-2, url);
        var id = ds.getDomainId(domain);
        assertEquals(domain, ds.getDomain(id));
    }

    @Test
    public void setDomainAlias() throws URISyntaxException {
        var ds = new EdgeDataStoreDaoImpl(dataSource);

        ds.putUrl(1.0, new EdgeUrl("https://marginalia.nu/"));

        ds.putDomainAlias(new EdgeDomain("marginalia.nu"), new EdgeDomain("www.marginalia.nu"));

        query("SELECT COUNT(*) FROM EC_DOMAIN", res -> {
            assertTrue(res.next());
            assertEquals(2, res.getInt(1));
        });

        query("SELECT COUNT(DISTINCT(QUALITY)) FROM EC_DOMAIN", res -> {
            assertTrue(res.next());
            assertEquals(1, res.getInt(1));
        });

        query("SELECT URL_PART, DOMAIN_ALIAS FROM EC_DOMAIN", res -> {

            while (res.next()) {
                System.out.println(res.getString(1) + ":" + res.getString(2));
                switch (res.getString(1)) {
                    case "https://marginalia.nu":
                        assertNotNull(res.getString(2));
                        break;
                    case "https://www.marginalia.nu":
                        assertNull(res.getString(2));
                        break;
                }
            }
        });
    }

    @Test
    void getUrlId() throws URISyntaxException {
        var ds = new EdgeDataStoreDaoImpl(dataSource);
        var url = new EdgeUrl("https://www.marginalia.nu/");

        ds.putUrl(-2, url);
        var id = ds.getUrlId(url);
        assertEquals(url, ds.getUrl(id));
    }

}