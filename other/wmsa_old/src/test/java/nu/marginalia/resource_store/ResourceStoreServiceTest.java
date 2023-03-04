package nu.marginalia.resource_store;

import lombok.SneakyThrows;
import nu.marginalia.client.Context;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.StaticResources;
import nu.marginalia.wmsa.renderer.WmsaServiceDescriptors;
import nu.marginalia.wmsa.resource_store.ResourceEntityStore;
import nu.marginalia.wmsa.resource_store.ResourceStoreClient;
import nu.marginalia.wmsa.resource_store.ResourceStoreService;
import nu.marginalia.wmsa.resource_store.model.RenderedResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ResourceStoreServiceTest {
    static ResourceStoreService service;
    static ResourceStoreClient client;

    static final int testPort = new Random().nextInt(4000, 10000);
    static ResourceEntityStore resourceStore;
    static Path tempDir;
    private static final Logger logger = LoggerFactory.getLogger(ResourceStoreServiceTest.class);

    @SneakyThrows
    @BeforeAll
    public static void setUpClass() {
        Spark.port(testPort);
        System.setProperty("service-name", "renderer");

        client = new ResourceStoreClient(WmsaServiceDescriptors.descriptors);
        client.setServiceRoute("127.0.0.1", testPort);
        tempDir = Files.createTempDirectory("ResourceStoreServiceTest");
        resourceStore  = new ResourceEntityStore(tempDir);
        service = new ResourceStoreService("127.0.0.1", testPort,
                resourceStore, new Initialization(), null, new StaticResources());

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
    public void sunnyDay() {
        client.putResource(Context.internal(), "test", new RenderedResource("index.html",  LocalDateTime.MAX,"Hello World")).blockingSubscribe();
        assertEquals("Hello World", client.getResource(Context.internal(),"test", "index.html").blockingFirst());
    }


    @Test
    public void loadFromDisk() throws InterruptedException {
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
    public void testReaper() {
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
    public void update() {
        client.putResource(Context.internal(), "test", new RenderedResource("index.html", LocalDateTime.MAX,"Hello World")).blockingSubscribe();
        assertEquals("Hello World", client.getResource(Context.internal(),"test", "index.html").blockingFirst());
        client.putResource(Context.internal(), "test", new RenderedResource("index.html", LocalDateTime.MAX,"Hello World 2")).blockingSubscribe();
        assertEquals("Hello World 2", client.getResource(Context.internal(), "test", "index.html").blockingFirst());
    }

    @Test
    public void missing() {
        var ret = client
                .getResource(Context.internal(), "test", "invalid.html")
                .onErrorReturnItem("Error")
                .blockingFirst();
        assertEquals("Error", ret);
    }

}