package nu.marginalia.wmsa.resource_store;

import lombok.SneakyThrows;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.resource_store.model.RenderedResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ResourceStoreServiceTest {
    static ResourceStoreService service;
    static ResourceStoreClient client;

    static int testPort = TestUtil.getPort();
    static ResourceEntityStore resourceStore;
    static Path tempDir;
    private static Logger logger = LoggerFactory.getLogger(ResourceStoreServiceTest.class);

    @SneakyThrows
    @BeforeAll
    public static void setUpClass() {
        Spark.port(testPort);
        System.setProperty("service-name", "renderer");

        client = new ResourceStoreClient();
        client.setServiceRoute("127.0.0.1", testPort);
        tempDir = Files.createTempDirectory("ResourceStoreServiceTest");
        resourceStore  = new ResourceEntityStore(tempDir);
        service = new ResourceStoreService("127.0.0.1", testPort, null,
                resourceStore, new Initialization(), null);

        Spark.awaitInitialization();
    }

    @AfterEach
    public void clearTempDir() {
        for (File f : tempDir.toFile().listFiles()) {
            for (File f2 : f.listFiles()) {
                logger.debug("Deleting {} -> {}", f2, f2.delete());
            }
            logger.debug("Deleting {} -> {}", f, f.delete());
        }
    }

    @AfterAll
    public static void tearDownAll() {
        tempDir.toFile().delete();
        Spark.awaitStop();
    }

    @Test
    public void sunnyDay() throws IOException {
        client.putResource(Context.internal(), "test", new RenderedResource("index.html",  LocalDateTime.MAX,"Hello World")).blockingSubscribe();
        assertEquals("Hello World", client.getResource(Context.internal(),"test", "index.html").blockingFirst());
    }


    @Test
    public void loadFromDisk() throws IOException, InterruptedException {
        client.putResource(Context.internal(), "test", new RenderedResource("index.html",  LocalDateTime.MAX,"Hello World")).blockingSubscribe();
        client.putResource(Context.internal(), "test", new RenderedResource("expired.html",  LocalDateTime.now().minusDays(14),"Hello World")).blockingSubscribe();

        var resourceStore2 = new ResourceEntityStore(tempDir, true);
        Thread.sleep(1000);
        var resource = resourceStore2.getResource("test", "index.html");

        assertNotNull(resource);
        assertEquals("Hello World", resource.data);

        assertNull(resourceStore2.getResource("test", "expired.html"));
    }

    @Test
    public void testReaper() throws IOException {
        client.putResource(Context.internal(), "test", new RenderedResource("index.html",  LocalDateTime.now().minusDays(14),"Hello World")).blockingSubscribe();
        assertEquals("Hello World", client.getResource(Context.internal(),"test", "index.html").blockingFirst());

        resourceStore.reapStaleResources();

        var ret = client
                .getResource(Context.internal(), "test", "index.html")
                .onErrorReturnItem("Error")
                .blockingFirst();
        assertEquals("Error", ret);
    }


    @Test
    public void update() throws IOException {
        client.putResource(Context.internal(), "test", new RenderedResource("index.html", LocalDateTime.MAX,"Hello World")).blockingSubscribe();
        assertEquals("Hello World", client.getResource(Context.internal(),"test", "index.html").blockingFirst());
        client.putResource(Context.internal(), "test", new RenderedResource("index.html", LocalDateTime.MAX,"Hello World 2")).blockingSubscribe();
        assertEquals("Hello World 2", client.getResource(Context.internal(), "test", "index.html").blockingFirst());
    }

    @Test
    public void missing() throws IOException {
        var ret = client
                .getResource(Context.internal(), "test", "invalid.html")
                .onErrorReturnItem("Error")
                .blockingFirst();
        assertEquals("Error", ret);
    }

}