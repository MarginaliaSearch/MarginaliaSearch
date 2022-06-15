package nu.marginalia.wmsa.edge.index.service;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.client.exception.RemoteException;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.index.EdgeIndexService;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.service.query.SearchIndexPartitioner;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import spark.Spark;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static nu.marginalia.util.TestUtil.getConnection;
import static nu.marginalia.wmsa.edge.index.EdgeIndexService.DYNAMIC_BUCKET_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
public class EdgeIndexClientTest {
    private static HikariDataSource dataSource;
    private static EdgeIndexService service;
    private static EdgeIndexClient client;
    private static Path tempDir;
    private static SearchIndexes indexes;

    @SneakyThrows
    public static HikariDataSource provideConnection() {
        return getConnection();
    }

    static final int testPort = TestUtil.getPort();

    @SneakyThrows
    @BeforeAll
    public static void setUpClass() {
        Spark.port(testPort);
        System.setProperty("service-name", "edge-index");

        dataSource = provideConnection();
        dataSource.setKeepaliveTime(100);
        dataSource.setIdleTimeout(100);
        client = new EdgeIndexClient();
        client.setServiceRoute("127.0.0.1", testPort);

        tempDir = Files.createTempDirectory("EdgeIndexClientTest");

        var servicesFactory = new IndexServicesFactory(tempDir,tempDir,tempDir,tempDir,
                "writer-index",
                "writer-dictionary",
                "index-words-read",
                "index-urls-read",
                "index-words-write",
                "index-urls-write",
                1L<<24,
                id->false,
                new SearchIndexPartitioner(null)
                );

        var init = new Initialization();
        indexes = new SearchIndexes(servicesFactory, new SearchIndexPartitioner(null));
        service = new EdgeIndexService("127.0.0.1",
                testPort,
                init, null,
                indexes);

        Spark.awaitInitialization();
        init.setReady();
    }

    @Test
    public void testMultiBucketHit() {
        putWords(1, 1, -2, "fancy", "anagram", "dilbert", "whoah", "engram");
        putWords(2, 2, -5, "quibble", "angry", "whoah", "fancy");
        putWords(3, 3, -0.01, "strong", "manly", "muscles");
        indexes.repartition();
        indexes.preconvert();
        indexes.reindexAll();

        var results = client.query(Context.internal(), EdgeSearchSpecification.justIncludes("fancy")).resultsList.get(IndexBlock.Title).get(0).results;
        System.out.println(results);
        List<EdgeId<EdgeUrl>> flatResults = results.values().stream().flatMap(List::stream).map(rs -> rs.url).collect(Collectors.toList());

        assertEquals(2, flatResults.size());
        assertTrue(flatResults.contains(new EdgeId<EdgeUrl>(1)));
        assertTrue(flatResults.contains(new EdgeId<EdgeUrl>(2)));
    }

    @Test
    public void testHighHit() {
        putWords(2, 5, -100, "trapphus");
        indexes.repartition();
        indexes.preconvert();
        indexes.reindexAll();
        var rsp = client.query(Context.internal(), EdgeSearchSpecification.justIncludes("trapphus"));
        System.out.println(rsp);
        assertEquals(5, rsp.resultsList.get(IndexBlock.Title).get(0).results.get(0).get(0).url.getId());
    }


    @Test
    public void testSearchDomain() {
        putWords(8, 1, -2, "domain");
        putWords(8, 2, -5, "domain");
        putWords(10, 3, -0.01, "domain");
        putWords(11, 3, -0.01, "domain");
        putWords(12, 3, -0.01, "domain");
        indexes.repartition();
        indexes.preconvert();
        indexes.reindexAll();

        var results = client.query(Context.internal(), EdgeSearchSpecification.justIncludes("fancy")).resultsList.get(IndexBlock.Title).get(0).results;
        System.out.println(results);
        List<EdgeId<EdgeUrl>> flatResults = results.values().stream().flatMap(List::stream).map(rs -> rs.url).collect(Collectors.toList());

        assertEquals(2, flatResults.size());
        assertTrue(flatResults.contains(new EdgeId<EdgeUrl>(1)));
        assertTrue(flatResults.contains(new EdgeId<EdgeUrl>(2)));
    }

    void putWords(int didx, int idx, double quality, String... words) {
        EdgePageWords epw = new EdgePageWords(IndexBlock.Title);
        epw.addAll(Arrays.asList(words));
        client.putWords(Context.internal(), new EdgeId<>(didx), new EdgeId<>(idx), quality,
                new EdgePageWordSet(epw), 0).blockingSubscribe();
    }

    @AfterAll
    public static void tearDownClass() {
        nu.marginalia.util.test.TestUtil.clearTempDir(tempDir);
    }

}
