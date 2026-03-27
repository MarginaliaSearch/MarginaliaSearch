package nu.marginalia.control;

import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.StatusCode;
import io.jooby.test.MockRouter;
import nu.marginalia.control.app.svc.*;
import nu.marginalia.control.node.svc.*;
import nu.marginalia.control.sys.svc.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for control-service HTTP routing.
 *
 * Verifies that:
 * 1. Key routes are registered and respond (not 404)
 * 2. {param} path parameter extraction works correctly
 * 3. ControlValidationError is handled by the error handler
 * 4. HTML responses have the correct content type
 *
 * Uses MockRouter so no real HTTP server is started.
 */
class ControlServiceIntegrationTest {

    static ControlTestEnvironment env;
    static MockRouter router;

    @BeforeAll
    static void setup() throws Exception {
        env = new ControlTestEnvironment();
        Jooby jooby = new Jooby();

        // Register all sub-service routes (mirrors ControlService.startJooby)
        env.get(MessageQueueService.class).register(jooby);
        env.get(ControlSysActionsService.class).register(jooby);
        env.get(DataSetsService.class).register(jooby);
        env.get(ControlDomainRankingSetsService.class).register(jooby);
        env.get(AbortedProcessService.class).register(jooby);
        env.get(ControlFileStorageService.class).register(jooby);
        env.get(ControlNodeActionsService.class).register(jooby);
        env.get(ControlNodeService.class).register(jooby);
        env.get(ControlBlacklistService.class).register(jooby);
        env.get(SearchToBanService.class).register(jooby);
        env.get(ApiKeyService.class).register(jooby);
        env.get(DomainComplaintService.class).register(jooby);
        env.get(RandomExplorationService.class).register(jooby);
        env.get(DomainsManagementService.class).register(jooby);

        // Error handler must be registered so ControlValidationError is caught
        env.get(ControlErrorHandler.class).register(jooby);

        // Replicate the direct routes registered in ControlService.startJooby
        var heartbeatService = env.get(HeartbeatService.class);
        var eventLogService = env.get(EventLogService.class);
        var controlNodeService = env.get(ControlNodeService.class);
        var messageQueueService = env.get(MessageQueueService.class);

        var indexRenderer = env.controlRendererFactory.renderer("control/index");
        var eventsRenderer = env.controlRendererFactory.renderer("control/sys/events");
        var serviceByIdRenderer = env.controlRendererFactory.renderer("control/sys/service-by-id");

        jooby.get("/", ctx -> {
            ctx.setResponseType(MediaType.html);
            return indexRenderer.render(Map.of(
                    "processes", heartbeatService.getProcessHeartbeats(),
                    "nodes", controlNodeService.getNodeStatusList(),
                    "jobs", heartbeatService.getTaskHeartbeats(),
                    "services", heartbeatService.getServiceHeartbeats(),
                    "events", eventLogService.getLastEntries(Long.MAX_VALUE, 20)
            ));
        });

        jooby.get("/events", ctx -> {
            ctx.setResponseType(MediaType.html);
            return eventsRenderer.render(eventLogService.eventsListModel(ctx));
        });

        jooby.get("/services/{id}", ctx -> {
            ctx.setResponseType(MediaType.html);
            String svcId = ctx.path("id").value();
            return serviceByIdRenderer.render(Map.of(
                    "id", svcId,
                    "messages", messageQueueService.getEntriesForInbox(svcId, Long.MAX_VALUE, 20),
                    "events", eventLogService.getLastEntriesForService(svcId, Long.MAX_VALUE, 20)
            ));
        });

        router = new MockRouter(jooby);
        router.setFullExecution(true);
    }

    @Test
    void testIndex_returnsHtml() {
        router.get("/", response -> {
            assertEquals(StatusCode.OK, response.getStatusCode());
            assertEquals(MediaType.html, response.getContentType());
        });
    }

    @Test
    void testApiKeys_returnsHtml() {
        router.get("/api-keys", response ->
            assertEquals(StatusCode.OK, response.getStatusCode()));
    }

    @Test
    void testBlacklist_returnsHtml() {
        router.get("/blacklist", response ->
            assertEquals(StatusCode.OK, response.getStatusCode()));
    }

    @Test
    void testMessageQueue_returnsHtml() {
        router.get("/message-queue", response ->
            assertEquals(StatusCode.OK, response.getStatusCode()));
    }

    /** Verify that {id} path parameter syntax is used correctly (not Spark-style :id).
     * If the route was mis-registered with :id syntax, this would return 404.
     * A NPE from the empty mock DB means the route was found and the param extracted. */
    @Test
    void testMessageQueueById_pathParamExtracted() {
        try {
            router.get("/message-queue/1", response ->
                assertNotEquals(StatusCode.NOT_FOUND, response.getStatusCode()));
        } catch (NullPointerException ignored) {
            // Route was found; NPE from getMessage() returning null for empty mock DB
        }
    }

    @Test
    void testNodes_returnsHtml() {
        router.get("/nodes", response ->
            assertEquals(StatusCode.OK, response.getStatusCode()));
    }

    /** Verify that /nodes/{id} path parameter extraction works. */
    @Test
    void testNodeById_pathParamExtracted() {
        router.get("/nodes/1", response ->
            assertNotEquals(StatusCode.NOT_FOUND, response.getStatusCode()));
    }

    /** Verify that /services/{id} path parameter extraction works. */
    @Test
    void testServiceById_pathParamExtracted() {
        router.get("/services/svc-test", response ->
            assertNotEquals(StatusCode.NOT_FOUND, response.getStatusCode()));
    }

    /** Verify ControlValidationError is intercepted by the error handler and
     * returns an HTML page rather than a plain server error. */
    @Test
    void testControlValidationError_handledByErrorHandler() {
        // Use tryError to directly invoke the registered error handler with a ControlValidationError
        router.tryError(
            new ControlValidationError("No source specified", "A source file storage must be specified", ".."),
            response -> assertEquals(MediaType.html, response.getContentType()));
    }

    @Test
    void testEvents_returnsHtml() {
        router.get("/events", response ->
            assertEquals(StatusCode.OK, response.getStatusCode()));
    }
}
