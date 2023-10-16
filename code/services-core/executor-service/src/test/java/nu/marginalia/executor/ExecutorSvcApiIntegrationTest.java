package nu.marginalia.executor;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Provides;
import nu.marginalia.actor.Actor;
import nu.marginalia.actor.ActorControlService;
import nu.marginalia.actor.task.CrawlJobExtractorActor;
import nu.marginalia.client.Context;
import nu.marginalia.client.route.RouteProvider;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.executor.model.crawl.RecrawlParameters;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.inbox.MqAsynchronousInbox;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeatImpl;
import nu.marginalia.service.descriptor.ServiceDescriptor;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import spark.Spark;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@Tag("slow")
public class ExecutorSvcApiIntegrationTest {

    static TestInstances testInstances;
    static final int port = 9999;

    @BeforeAll
    public static void setUpAll() {
        RouteProvider.setDefaultPort(port);
        var injector = Guice.createInjector(new TestModule());
        testInstances = injector.getInstance(TestInstances.class);
        injector.getInstance(Initialization.class).setReady();
    }

    @AfterAll
    public static void tearDownAll() {
        RouteProvider.resetDefaultPort();
    }

    @BeforeEach
    public void setUp() {
        Mockito.reset(testInstances.actorControlService);
    }

    @AfterAll
    public static void tearDown() {
        Spark.stop();
    }

    @Test
    public void startStartActor() throws Exception {
        testInstances.client.startFsm(Context.internal(), 0, "crawl");
        Mockito.verify(testInstances.actorControlService).startJSON(Actor.CRAWL, "\"\"");
    }

    @Test
    public void startStopActor() {
        testInstances.client.stopFsm(Context.internal(), 0, "crawl");

        Mockito.verify(testInstances.actorControlService).stop(Actor.CRAWL);
    }

    @Test
    public void triggerCrawl() throws Exception {
        testInstances.client.triggerCrawl(Context.internal(), 0, "1");

        Mockito.verify(testInstances.actorControlService).start(eq(Actor.CRAWL), any());
    }

    @Test
    public void triggerRecrawl() throws Exception {
        testInstances.client.triggerRecrawl(Context.internal(), 0,
                new RecrawlParameters(new FileStorageId(0), List.of()));

        Mockito.verify(testInstances.actorControlService).start(eq(Actor.RECRAWL), any());
    }

    @Test
    public void triggerConvert() throws Exception {
        testInstances.client.triggerConvert(Context.internal(), 0, "1");

        Mockito.verify(testInstances.actorControlService).startFrom(eq(Actor.CONVERT), eq("CONVERT"), any());
    }

    @Test
    public void triggerProcessAndLoad() throws Exception {
        testInstances.client.triggerProcessAndLoad(Context.internal(), 0, "1");

        Mockito.verify(testInstances.actorControlService).start(eq(Actor.CONVERT_AND_LOAD), any());
    }

    @Test
    public void loadProcessedData() throws Exception {
        testInstances.client.loadProcessedData(Context.internal(), 0, "1");

        Mockito.verify(testInstances.actorControlService).startFrom(
                eq(Actor.CONVERT_AND_LOAD),
                eq("LOAD"),
                any());
    }

    @Test
    public void calculateAdjacencies() throws Exception {
        testInstances.client.calculateAdjacencies(Context.internal(), 0);

        Mockito.verify(testInstances.actorControlService).start(eq(Actor.ADJACENCY_CALCULATION));
    }

    @Test
    public void sideloadDirtree() throws Exception {
        testInstances.client.sideloadDirtree(Context.internal(), 0, Path.of("/tmp/test"));

        Mockito.verify(testInstances.actorControlService).startFrom(eq(Actor.CONVERT), eq("CONVERT_DIRTREE"), eq("/tmp/test"));
    }
    @Test
    public void sideloadEncyclopedia() throws Exception {
        testInstances.client.sideloadEncyclopedia(Context.internal(), 0, Path.of("/tmp/test"));

        Mockito.verify(testInstances.actorControlService).startFrom(eq(Actor.CONVERT), eq("CONVERT_ENCYCLOPEDIA"), eq("/tmp/test"));
    }
    @Test
    public void sideloadStackexchange() throws Exception {
        testInstances.client.sideloadStackexchange(Context.internal(), 0, Path.of("/tmp/test"));

        Mockito.verify(testInstances.actorControlService).startFrom(eq(Actor.CONVERT), eq("CONVERT_STACKEXCHANGE"), eq("/tmp/test"));
    }

    @Test
    public void testCreateCrawlSpecFromDb() throws Exception {
        testInstances.client.createCrawlSpecFromDb(Context.internal(), 0, "Lorem Ipsum");
        Mockito.verify(testInstances.actorControlService).startFrom(eq(Actor.CRAWL_JOB_EXTRACTOR), eq("CREATE_FROM_DB"), eq(new CrawlJobExtractorActor.CrawlJobExtractorArguments("Lorem Ipsum")));
    }

    @Test
    public void testCreateCrawlSpecFromUrl() throws Exception {
        testInstances.client.createCrawlSpecFromDownload(Context.internal(), 0, "Lorem Ipsum", "http://www.example.com");
        Mockito.verify(testInstances.actorControlService).startFrom(eq(Actor.CRAWL_JOB_EXTRACTOR), eq("CREATE_FROM_LINK"), eq(new CrawlJobExtractorActor.CrawlJobExtractorArgumentsWithURL("Lorem Ipsum", "http://www.example.com")));
    }

    @Test
    public void backupRestore() throws Exception {
        testInstances.client.restoreBackup(Context.internal(), 0, "1");

        Mockito.verify(testInstances.actorControlService).startFrom(eq(Actor.RESTORE_BACKUP), eq("RESTORE"), eq(new FileStorageId(1)));
    }
}

class TestInstances {
    @Inject
    ExecutorSvc service;
    @Inject
    ExecutorClient client;
    @Inject
    ActorControlService actorControlService;
}
class TestModule extends AbstractModule  {

    @Override
    public void configure() {
        System.setProperty("service-name", "test");
        bind(ActorControlService.class).toInstance(Mockito.mock(ActorControlService.class));
        bind(FileStorageService.class).toInstance(Mockito.mock(FileStorageService.class));
        bind(ProcessService.class).toInstance(Mockito.mock(ProcessService.class));
        bind(ServiceEventLog.class).toInstance(Mockito.mock(ServiceEventLog.class));
        bind(ServiceHeartbeatImpl.class).toInstance(Mockito.mock(ServiceHeartbeatImpl.class));
        bind(MetricsServer.class).toInstance(Mockito.mock(MetricsServer.class));
        bind(IndexClient.class).toInstance(Mockito.mock(IndexClient.class));
        bind(ProcessOutboxes.class).toInstance(Mockito.mock(ProcessOutboxes.class));
        bind(ServiceConfiguration.class)
                .toInstance(new ServiceConfiguration(ServiceId.Executor,
                        0, "127.0.0.1", ExecutorSvcApiIntegrationTest.port, -1, UUID.randomUUID()));
    }

    @Provides
    public ServiceDescriptors getServiceDescriptors() {
        return new ServiceDescriptors(
                List.of(new ServiceDescriptor(ServiceId.Executor, "127.0.0.1"))
        );
    }

    @Provides
    public MessageQueueFactory getMessageQueueFactory() {
        var mock = Mockito.mock(MessageQueueFactory.class);

        Mockito.when(mock.createAsynchronousInbox(Mockito.anyString(), Mockito.anyInt(), any())).
                thenReturn(Mockito.mock(MqAsynchronousInbox.class));

        return mock;
    }
}