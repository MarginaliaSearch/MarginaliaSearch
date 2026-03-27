package nu.marginalia.dating;

import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.test.MockContext;
import io.jooby.test.MockRouter;
import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.browse.DbBrowseDomainsSimilarCosine;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.inbox.MqSynchronousInbox;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeatImpl;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import org.junit.jupiter.api.*;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the dating-service HTTP routes.
 *
 * <p>These tests instantiate a real DatingService with mocked dependencies and
 * call its startJooby method to register the production routes. MockRouter is
 * used to exercise the actual route handlers without starting an HTTP server.</p>
 *
 * <p>Limitation: Jooby MockRouter does not support real session state across
 * separate requests. Routes that depend on an established session (e.g. /view,
 * /next, /similar, /rewind with a valid session) will follow the
 * no-session redirect path. The /init (and /) route that creates sessions can
 * be verified to not error, but cross-request session persistence cannot be
 * tested through MockRouter alone.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatingServiceRoutesTest {

    static MockRouter router;
    static DatingService datingService;

    @SuppressWarnings("unchecked")
    @BeforeAll
    static void setup() throws Exception {
        // JoobyService.auditRequestIn uses System.getProperty("service-name") for
        // Prometheus counter labels; a null value causes IllegalArgumentException.
        System.setProperty("service-name", "dating-service");

        // -- Construct BaseServiceParams with mocked infrastructure --

        ServiceConfiguration configuration = new ServiceConfiguration(
                ServiceId.Dating,
                0,
                "127.0.0.1",
                "127.0.0.1",
                0,
                UUID.randomUUID()
        );

        Initialization initialization = Initialization.already();

        ServiceRegistryIf serviceRegistry = mock(ServiceRegistryIf.class);
        when(serviceRegistry.registerService(any(), any(), anyString()))
                .thenReturn(new ServiceEndpoint("127.0.0.1", 0));

        MqSynchronousInbox mqInbox = mock(MqSynchronousInbox.class);
        MessageQueueFactory mqFactory = mock(MessageQueueFactory.class);
        when(mqFactory.createSynchronousInbox(anyString(), anyInt(), any(UUID.class)))
                .thenReturn(mqInbox);

        MetricsServer metricsServer = mock(MetricsServer.class);
        ServiceHeartbeatImpl heartbeat = mock(ServiceHeartbeatImpl.class);
        ServiceEventLog eventLog = mock(ServiceEventLog.class);

        BaseServiceParams params = new BaseServiceParams(
                configuration,
                initialization,
                metricsServer,
                heartbeat,
                eventLog,
                serviceRegistry,
                mqFactory
        );

        // -- Mock DatingService-specific dependencies --

        DomainBlacklist blacklist = mock(DomainBlacklist.class);
        DbBrowseDomainsSimilarCosine browseSimilarCosine = mock(DbBrowseDomainsSimilarCosine.class);
        DbBrowseDomainsRandom browseRandom = mock(DbBrowseDomainsRandom.class);
        ScreenshotService screenshotService = mock(ScreenshotService.class);

        when(screenshotService.hasScreenshot(any(Integer.class))).thenReturn(true);

        BrowseResult browseResult = makeBrowseResult(1);
        when(browseRandom.getRandomDomains(anyInt(), any(), anyInt()))
                .thenReturn(List.of(browseResult));

        RendererFactory rendererFactory = mock(RendererFactory.class);
        MustacheRenderer<BrowseResult> datingRenderer = mock(MustacheRenderer.class);
        when(rendererFactory.<BrowseResult>renderer("dating/dating-view")).thenReturn(datingRenderer);
        when(datingRenderer.render(any())).thenReturn("<html>dating</html>");
        when(datingRenderer.render(any(), any())).thenReturn("<html>dating</html>");

        // -- Construct real DatingService and register routes --

        datingService = new DatingService(
                params,
                rendererFactory,
                blacklist,
                browseSimilarCosine,
                browseRandom,
                screenshotService
        );

        Jooby jooby = new Jooby();
        datingService.startJooby(jooby);

        router = new MockRouter(jooby);
        router.setFullExecution(true);
    }

    private static BrowseResult makeBrowseResult(int id) {
        try {
            return new BrowseResult(new EdgeUrl("http://example" + id + ".com/"), id, 1.0, true);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------
    // / (init session) -- creates a session and redirects to /view
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void testInit_doesNotError() {
        router.get("/", new MockContext(), rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error on /: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // /init (alias for /)
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void testInitAlias_doesNotError() {
        router.get("/init", new MockContext(), rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error on /init: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // /view (without session -- should redirect)
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void testView_noSession_doesNotError() {
        // Without a valid session, the handler redirects to the front page.
        // MockRouter does not persist session state, so this always follows
        // the no-session path.
        router.get("/view", new MockContext(), rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error on /view: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // /next (without session -- should redirect)
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void testNext_noSession_doesNotError() {
        router.get("/next", new MockContext(), rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error on /next: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // /rewind (without session -- should redirect)
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void testRewind_noSession_doesNotError() {
        router.get("/rewind", new MockContext(), rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error on /rewind: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // /reset (without session -- should redirect)
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void testReset_noSession_doesNotError() {
        router.get("/reset", new MockContext(), rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error on /reset: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // /similar/{id} (without session -- should redirect)
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void testSimilar_noSession_doesNotError() {
        router.get("/similar/1", new MockContext(), rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error on /similar/1: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // Verify internal health endpoints registered by JoobyService.startJooby
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void testInternalPing_returnsOk() {
        router.get("/internal/ping", new MockContext(), rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals("pong", rsp.value());
        });
    }

    @Test
    @Order(11)
    void testInternalStarted_returnsOk() {
        // Initialization was set to already-ready, so this should return "ok"
        router.get("/internal/started", new MockContext(), rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals("ok", rsp.value());
        });
    }

    @Test
    @Order(12)
    void testInternalReady_returnsOk() {
        router.get("/internal/ready", new MockContext(), rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals("ok", rsp.value());
        });
    }

    // ------------------------------------------------------------------
    // Verify non-existent routes return 404
    // ------------------------------------------------------------------

    @Test
    @Order(20)
    void testUnknownRoute_returns404() {
        router.get("/nonexistent", new MockContext(), rsp ->
            assertEquals(StatusCode.NOT_FOUND, rsp.getStatusCode()));
    }
}
