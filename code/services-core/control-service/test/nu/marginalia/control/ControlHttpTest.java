package nu.marginalia.control;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.ExecutionMode;
import io.jooby.Jooby;
import io.jooby.Server;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.browse.RandomDomainSuggestionsDao;
import nu.marginalia.control.actor.ControlActorService;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.executor.client.*;
import nu.marginalia.index.api.IndexMqClient;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.schedule.ActorScheduleRow;
import nu.marginalia.schedule.ActorScheduleService;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.ServiceMonitors;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeatImpl;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControlHttpTest {
    private final HikariDataSource dataSource = mock(HikariDataSource.class, RETURNS_DEEP_STUBS);
    private final ExecutorClient executor = mock(ExecutorClient.class);
    private final ExecutorCrawlClient crawler = mock(ExecutorCrawlClient.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ActorScheduleService schedules = mock(ActorScheduleService.class);
    private final RandomDomainSuggestionsDao suggestions = mock(RandomDomainSuggestionsDao.class);
    private Jooby app;
    private Server server;
    private HttpClient client;
    private String baseUrl;
    private String previousServiceName;
    private Thread.UncaughtExceptionHandler previousExceptionHandler;
    @TempDir Path tempDir;

    @BeforeAll
    void startServer() {
        previousServiceName = System.setProperty("service-name", "control-service");
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        var main = Guice.createInjector(new ControlProcessModule(), new TestModule()).getInstance(ControlMain.class);
        app = new Jooby().setExecutionMode(ExecutionMode.WORKER);
        main.start(app);
        server = main.server();
        server.start(app);
        baseUrl = "http://127.0.0.1:" + server.getOptions().getPort();
        client = HttpClient.newHttpClient();
    }

    private class TestModule extends AbstractModule {
        @Provides
        ServiceRegistryIf registry() throws Exception {
            var registry = mock(ServiceRegistryIf.class);
            when(registry.registerService(any(), any(), any())).thenReturn(new ServiceEndpoint("127.0.0.1", 0));
            return registry;
        }

        @Override
        protected void configure() {
            var initialization = mock(Initialization.class);
            when(initialization.isReady()).thenReturn(true);
            bind(ServiceConfiguration.class).toInstance(new ServiceConfiguration(
                    ServiceId.Control, 0, "127.0.0.1", "127.0.0.1", 0, UUID.randomUUID()));
            bind(Initialization.class).toInstance(initialization);
            bind(MetricsServer.class).toInstance(mock(MetricsServer.class));
            bind(ServiceHeartbeatImpl.class).toInstance(mock(ServiceHeartbeatImpl.class));
            bind(ServiceEventLog.class).toInstance(mock(ServiceEventLog.class));
            bind(ServiceMonitors.class).toInstance(mock(ServiceMonitors.class));
            bind(MessageQueueFactory.class).toInstance(mock(MessageQueueFactory.class, RETURNS_DEEP_STUBS));
            bind(MqPersistence.class).toInstance(mock(MqPersistence.class));
            bind(HikariDataSource.class).toInstance(dataSource);
            bind(ControlActorService.class).toInstance(mock(ControlActorService.class));
            bind(NodeConfigurationService.class).toInstance(mock(NodeConfigurationService.class));
            bind(FileStorageService.class).toInstance(storage);
            bind(ExecutorClient.class).toInstance(executor);
            bind(ExecutorCrawlClient.class).toInstance(crawler);
            bind(ExecutorExportClient.class).toInstance(mock(ExecutorExportClient.class));
            bind(ExecutorSideloadClient.class).toInstance(mock(ExecutorSideloadClient.class));
            bind(QueryClient.class).toInstance(mock(QueryClient.class));
            bind(IndexMqClient.class).toInstance(mock(IndexMqClient.class));
            bind(DbDomainQueries.class).toInstance(mock(DbDomainQueries.class));
            bind(DomainTypes.class).toInstance(mock(DomainTypes.class));
            bind(DomainRankingSetsService.class).toInstance(mock(DomainRankingSetsService.class));
            bind(ActorScheduleService.class).toInstance(schedules);
            bind(RandomDomainSuggestionsDao.class).toInstance(suggestions);
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(dataSource, executor, crawler, storage, schedules, suggestions);
    }

    @AfterAll
    void stopServer() {
        if (client != null) client.close();
        if (server != null) server.stop();
        Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler);
        if (previousServiceName == null) System.clearProperty("service-name");
        else System.setProperty("service-name", previousServiceName);
    }

    @Test
    void preservesRouteContract() throws Exception {
        try (var input = getClass().getResourceAsStream("/control-routes.txt")) {
            var expected = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().collect(Collectors.toSet());
            var actual = app.getRoutes().stream().map(route -> route.getMethod() + " " + route.getPattern())
                    .collect(Collectors.toSet());
            assertEquals(expected, actual);
        }
    }

    @Test
    void rendersExistingTemplatesAndJson() throws Exception {
        for (String path : List.of("/", "/actions", "/events", "/api-keys", "/schedules", "/nodes")) {
            var response = get(path);
            assertEquals(200, response.statusCode(), path + ": " + response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
            assertTrue(response.body().contains("<html"), path);
        }
        assertTrue(get("/actions").body().contains("System Actions"));
        var heartbeats = get("/heartbeats");
        assertEquals(200, heartbeats.statusCode());
        assertTrue(heartbeats.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertEquals("[]", heartbeats.body());
    }

    @Test
    void servesStaticAssetsAndHealthChecks() throws Exception {
        var css = get("/tables.css");
        assertEquals(200, css.statusCode());
        assertTrue(css.headers().firstValue("Content-Type").orElseThrow().startsWith("text/css"));
        var cached = send(HttpRequest.newBuilder(URI.create(baseUrl + "/tables.css"))
                .header("If-None-Match", css.headers().firstValue("ETag").orElseThrow()).GET());
        assertEquals(304, cached.statusCode());
        assertEquals(404, get("/missing.css").statusCode());
        assertEquals("pong", get("/internal/ping").body());
        assertEquals("ok", get("/internal/started").body());
        assertEquals("ok", get("/internal/ready").body());
    }

    @Test
    void acceptsScheduleFormsAndQueryParameters() throws Exception {
        var response = post("/schedules?type=window", "scheduleName=SCREENGRAB&startHour=2&endHour=6");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("URL='/schedules'"));
        verify(schedules).updateWindow(ActorScheduleRow.Window.SCREENGRAB, 2, 6);
    }

    @Test
    void handlesRepeatedFormFieldsAndAcknowledgement() throws Exception {
        var response = post("/nodes/3/actions/load", "source=12&source=13");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("Loading"));
        verify(crawler).loadProcessedData(3, List.of(new FileStorageId(12), new FileStorageId(13)));
    }

    @Test
    void enumeratesSelectedFormFields() throws Exception {
        var response = post("/random-domains/suggestions/approve?after=9", "domain-12=on&domain-13=on&domain-14=off");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("/random-domains/suggestions?after=9"));
        verify(suggestions).approveSuggestions(new int[]{12, 13});
    }

    @Test
    void rendersValidationErrorsWithoutExecutingAction() throws Exception {
        var response = post("/nodes/3/actions/download-sample-data", "sample=invalid");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("A valid sample data set must be specified"));
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        verifyNoInteractions(executor);
    }

    @Test
    void preservesApiKeyDeleteMethodsAndBadInputStatus() throws Exception {
        var statement = mock(PreparedStatement.class);
        when(dataSource.getConnection().prepareStatement(anyString())).thenReturn(statement);
        assertEquals(200, send(HttpRequest.newBuilder(URI.create(baseUrl + "/api-keys/test-key")).DELETE()).statusCode());
        assertEquals(200, post("/api-keys/test-key/delete", "").statusCode());
        verify(statement, times(2)).setString(1, "test-key");
        assertEquals(400, post("/api-keys", "license=&name=&email=&rate=0").statusCode());
    }

    @Test
    void streamsDownloadsAndHandlesMissingFiles() throws Exception {
        var file = tempDir.resolve("example.log");
        Files.writeString(file, "test download\n");
        when(executor.remoteFileURL(any(), eq("example.log"))).thenReturn(file.toUri().toURL());
        var response = get("/nodes/3/storage/12/transfer?path=example.log");
        assertEquals(200, response.statusCode());
        assertEquals("test download\n", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/plain"));
        assertEquals("attachment; filename=\"example.log\"", response.headers().firstValue("Content-Disposition").orElseThrow());
        when(executor.remoteFileURL(any(), eq("missing.log"))).thenReturn(tempDir.resolve("missing.log").toUri().toURL());
        assertEquals(404, get("/nodes/3/storage/12/transfer?path=missing.log").statusCode());
    }

    @Test
    void streamsScreenshotsAndRendersPlaceholder() throws Exception {
        assertTrue(get("/screenshot/12").body().contains("<svg"));
        var statement = mock(PreparedStatement.class, RETURNS_DEEP_STUBS);
        when(dataSource.getConnection().prepareStatement(anyString())).thenReturn(statement);
        var results = statement.executeQuery();
        when(results.next()).thenReturn(true);
        when(results.getString(1)).thenReturn("image/png");
        var blob = mock(Blob.class);
        when(results.getBlob(2)).thenReturn(blob);
        when(blob.getBinaryStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/screenshot/12")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertEquals("image/png", response.headers().firstValue("Content-Type").orElseThrow());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    private HttpResponse<String> post(String path, String form) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
