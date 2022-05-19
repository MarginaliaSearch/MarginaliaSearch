package nu.marginalia.wmsa.edge.archive;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.archive.archiver.Archiver;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import spark.Spark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.util.TestUtil.getPort;
import static nu.marginalia.util.test.TestUtil.clearTempDir;

@Execution(ExecutionMode.SAME_THREAD)
public class ArchiveTest {
    static EdgeArchiveService service;

    static final int testPort = getPort();
    private static Path tempPath;
    private static Path tempPath2;
    private static ArchiveClient archiveClient;
    private static Archiver archiver;

    @BeforeAll
    public static void setUpClass() throws IOException {
        Spark.port(testPort);
        System.setProperty("service-name", "edge-archive");
        archiveClient = new ArchiveClient();
        archiveClient.setServiceRoute("127.0.0.1", testPort);

        tempPath = Files.createTempDirectory("archiveTest");
        tempPath2 = Files.createTempDirectory("wikiTest");

        archiver = new Archiver(tempPath, 10);
        service = new EdgeArchiveService("127.0.0.1", testPort,
                tempPath,
                archiver,
                new Initialization(), null);

        Spark.awaitInitialization();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        archiver.close();
        archiveClient.close();
        clearTempDir(tempPath);
        clearTempDir(tempPath2);
    }

    @SneakyThrows
    @Test
    public void testWiki() {
        var url = "Plato_(Disambiguation)";

        Assertions.assertFalse(archiveClient.hasWiki(Context.internal(), url).blockingFirst());

        archiveClient.submitWiki(Context.internal(), url, "<h1>Hello</h1>").blockingFirst();
        Assertions.assertTrue(archiveClient.hasWiki(Context.internal(), url).blockingFirst());
        Assertions.assertEquals("<h1>Hello</h1>", archiveClient.getWiki(Context.internal(), url).blockingFirst());
    }

}
