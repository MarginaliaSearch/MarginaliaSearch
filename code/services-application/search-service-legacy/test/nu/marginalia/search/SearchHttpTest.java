package nu.marginalia.search;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.ExecutionMode;
import io.jooby.Jooby;
import io.jooby.Server;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.domains.DomainInfoClient;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.api.livecapture.LiveCaptureClient;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.index.api.IndexMqClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.renderer.config.HandlebarsConfigurator;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.command.CommandEvaluator;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.exceptions.RedirectException;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeatImpl;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchHttpTest {
    private final CommandEvaluator commands = mock(CommandEvaluator.class);
    private final DbDomainQueries domains = mock(DbDomainQueries.class);
    private final SearchFlagSiteService flags = mock(SearchFlagSiteService.class);
    private final SearchCrosstalkService crosstalk = mock(SearchCrosstalkService.class);
    private Server server;
    private HttpClient client;
    private String baseUrl;
    private String previousServiceName;
    private Thread.UncaughtExceptionHandler previousExceptionHandler;

    @BeforeAll
    void startServer() throws Exception {
        previousServiceName = System.setProperty("service-name", "search-service");
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        var main = Guice.createInjector(new TestModule()).getInstance(SearchMain.class);
        var app = new Jooby().setExecutionMode(ExecutionMode.WORKER);
        main.start(app);
        server = main.server();
        server.start(app);
        baseUrl = "http://127.0.0.1:" + server.getOptions().getPort();
        client = HttpClient.newHttpClient();
    }

    private class TestModule extends AbstractModule {
        @Provides
        ServiceRegistryIf serviceRegistry() throws Exception {
            var registry = mock(ServiceRegistryIf.class);
            when(registry.registerService(any(), any(), any())).thenReturn(new ServiceEndpoint("127.0.0.1", 0));
            return registry;
        }

        @Override
        protected void configure() {
            var initialization = mock(Initialization.class);
            when(initialization.isReady()).thenReturn(true);

            bind(ServiceConfiguration.class).toInstance(new ServiceConfiguration(
                    ServiceId.Search, 0, "127.0.0.1", "127.0.0.1", 0, UUID.randomUUID()));
            bind(Initialization.class).toInstance(initialization);
            bind(MetricsServer.class).toInstance(mock(MetricsServer.class));
            bind(ServiceHeartbeatImpl.class).toInstance(mock(ServiceHeartbeatImpl.class));
            bind(ServiceEventLog.class).toInstance(mock(ServiceEventLog.class));
            bind(MessageQueueFactory.class).toInstance(mock(MessageQueueFactory.class, RETURNS_DEEP_STUBS));
            bind(HikariDataSource.class).toInstance(mock(HikariDataSource.class, RETURNS_DEEP_STUBS));

            bind(WebsiteUrl.class).toInstance(new WebsiteUrl("https://search.example/"));
            bind(HandlebarsConfigurator.class).to(SearchHandlebarsConfigurator.class);
            bind(CommandEvaluator.class).toInstance(commands);
            bind(DbDomainQueries.class).toInstance(domains);
            bind(SearchFlagSiteService.class).toInstance(flags);
            bind(SearchCrosstalkService.class).toInstance(crosstalk);
            bind(SearchOperator.class).toInstance(mock(SearchOperator.class));
            bind(SearchQueryCountService.class).toInstance(mock(SearchQueryCountService.class));
            bind(ScrapeStopper.class).toInstance(mock(ScrapeStopper.class));
            bind(IndexMqClient.class).toInstance(mock(IndexMqClient.class));
            bind(DomainInfoClient.class).toInstance(mock(DomainInfoClient.class));
            bind(FeedsClient.class).toInstance(mock(FeedsClient.class));
            bind(LiveCaptureClient.class).toInstance(mock(LiveCaptureClient.class));
            bind(ScreenshotService.class).toInstance(mock(ScreenshotService.class));
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(commands, domains, flags, crosstalk);
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
    void rendersHandlebarsAndRss() throws Exception {
        var index = get("/");
        assertEquals(200, index.statusCode());
        assertTrue(index.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        assertTrue(index.body().contains("<html"));
        assertEquals("public,max-age=3600", index.headers().firstValue("Cache-Control").orElseThrow());
        var news = get("/news.xml");
        assertEquals(200, news.statusCode());
        assertTrue(news.headers().firstValue("Content-Type").orElseThrow().startsWith("application/rss+xml"));
        assertTrue(news.body().contains("<rss"));
    }

    @Test
    void servesAssetsAndConditionalRequests() throws Exception {
        var script = get("/main.js");
        assertEquals(200, script.statusCode());
        assertTrue(script.headers().firstValue("Content-Type").orElseThrow().contains("javascript"));
        assertFalse(script.body().isBlank());
        assertEquals("max-age=600", script.headers().firstValue("Cache-Control").orElseThrow());
        var cached = send(HttpRequest.newBuilder(URI.create(baseUrl + "/main.js"))
                .header("If-None-Match", script.headers().firstValue("ETag").orElseThrow()).GET());
        assertEquals(304, cached.statusCode());
        assertTrue(cached.body().isEmpty());
        var openSearch = get("/opensearch.xml");
        assertEquals(200, openSearch.statusCode());
        assertTrue(openSearch.headers().firstValue("Content-Type").orElseThrow()
                .startsWith("application/opensearchdescription+xml"));
        assertTrue(openSearch.body().contains("OpenSearchDescription"));
        assertEquals(200, get("/serp.css").statusCode());
        assertEquals(404, get("/missing.js").statusCode());
    }

    @Test
    void parsesSearchParametersAndKeepsHtmlContentType() throws Exception {
        when(commands.eval(any())).thenReturn("<html>Results</html>");
        var response = get("/search?query=hello+world&page=2&js=no&sst=token");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        assertEquals("<html>Results</html>", response.body());
        var parameters = ArgumentCaptor.forClass(SearchParameters.class);
        verify(commands).eval(parameters.capture());
        assertEquals("hello world", parameters.getValue().query());
        assertEquals(2, parameters.getValue().page());
    }

    @Test
    void redirectsMissingQueriesAndCommands() throws Exception {
        for (String path : new String[]{"/search", "/search?query=", "/search?query=test&page=bad"}) {
            var response = get(path);
            assertEquals(302, response.statusCode());
            assertEquals("https://search.example/", response.headers().firstValue("Location").orElseThrow());
        }
        when(commands.eval(any())).thenThrow(new RedirectException("https://example.org/"));
        var response = get("/search?query=test");
        assertEquals(302, response.statusCode());
        assertEquals("https://example.org/", response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void redirectsSiteSearchWildcard() throws Exception {
        var response = get("/site-search/example.org/hello%20world?profile=blogs");
        assertEquals(302, response.statusCode());
        assertEquals("https://search.example/search?query=hello+world+site%3Aexample.org&profile=blogs",
                response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void rejectsPrefetch() throws Exception {
        for (String path : new String[]{"/search?query=test", "/site/example.org"}) {
            var response = send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("sec-purpose", "prefetch").GET());
            assertEquals(400, response.statusCode());
        }
        verifyNoInteractions(commands, domains);
    }

    @Test
    void acceptsReportFormWithQueryParameters() throws Exception {
        when(domains.getDomainId(any())).thenReturn(123);
        var response = post("/site/example.org?view=report&sst=token",
                "category=spam&description=Test+report&sampleQuery=hello+world");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<html"));
        verify(flags).insertComplaint(new SearchFlagSiteService.FlagSiteFormData(123, "spam", "Test report", "hello world"));
    }

    @Test
    void acceptsCrawlSuggestionFormAndReturns404ForUnknownDomain() throws Exception {
        when(domains.getDomain(123)).thenReturn(Optional.of(new EdgeDomain("example.org")));
        var response = post("/site/suggest/", "id=123&nomisclick=on");
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/site/example.org"));
        assertEquals(404, post("/site/suggest/", "id=999&nomisclick=on").statusCode());
    }

    @Test
    void rendersErrorTemplateForUnhandledExceptions() throws Exception {
        when(crosstalk.handle(any())).thenThrow(new IllegalStateException("test failure"));
        var response = get("/crosstalk/?query=hello");
        assertEquals(500, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        assertTrue(response.body().contains("An error occurred when communicating"));
    }

    @Test
    void servesHealthEndpoints() throws Exception {
        assertEquals("pong", get("/internal/ping").body());
        assertEquals("ok", get("/internal/started").body());
        assertEquals("ok", get("/internal/ready").body());
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
