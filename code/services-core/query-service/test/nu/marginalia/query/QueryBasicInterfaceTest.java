package nu.marginalia.query;

import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.StatusCode;
import io.jooby.test.MockContext;
import io.jooby.test.MockRouter;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the query-service HTTP routes.
 *
 * <p>All dependencies are mocked; no external processes or databases are needed.
 * The tests exercise routing, content-type negotiation, and basic response
 * correctness after the Spark-to-Jooby migration.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QueryBasicInterfaceTest {

    static MockRouter router;
    static QueryGRPCService queryGRPCService;

    @SuppressWarnings("unchecked")
    @BeforeAll
    static void setup() throws Exception {
        queryGRPCService = mock(QueryGRPCService.class);

        // Default: return an empty result set for any query
        when(queryGRPCService.executeDirect(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(new QueryGRPCService.DetailedDirectResult(null, List.of(), 0));

        var rendererFactory = mock(RendererFactory.class);
        var basicRenderer = mock(MustacheRenderer.class);
        var qdebugRenderer = mock(MustacheRenderer.class);

        when(rendererFactory.renderer("search")).thenReturn(basicRenderer);
        when(rendererFactory.renderer("qdebug")).thenReturn(qdebugRenderer);
        when(basicRenderer.render(any())).thenReturn("<html>search</html>");
        when(basicRenderer.render(any(), any())).thenReturn("<html>search</html>");
        when(qdebugRenderer.render(any())).thenReturn("<html>qdebug</html>");
        when(qdebugRenderer.render(any(), any())).thenReturn("<html>qdebug</html>");

        var queryBasicInterface = new QueryBasicInterface(rendererFactory, queryGRPCService);

        Jooby jooby = new Jooby();
        jooby.get("/search", queryBasicInterface::handleBasic);
        jooby.get("/qdebug", queryBasicInterface::handleAdvanced);

        router = new MockRouter(jooby);
        router.setFullExecution(true);
    }

    // ------------------------------------------------------------------
    // /search
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void testSearch_noQuery_rendersEmptyPage() {
        MockContext ctx = new MockContext();

        router.get("/search", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertNonEmptyStringBody(rsp);
        });
    }

    @Test
    @Order(2)
    void testSearch_withQuery_rendersResults() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("q=test");

        router.get("/search", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertNonEmptyStringBody(rsp);
        });
    }

    @Test
    @Order(3)
    void testSearch_withQuery_jsonAccept_returnsJson() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("q=test");
        ctx.setRequestHeader("Accept", "application/json");

        router.get("/search", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertTrue(rsp.getContentType().toString().contains("json"),
                    "Expected JSON content-type but got: " + rsp.getContentType());
        });
    }

    @Test
    @Order(4)
    void testSearch_withPagination() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("q=test&count=20&page=2&domainCount=3");

        router.get("/search", ctx, rsp ->
            assertEquals(StatusCode.OK, rsp.getStatusCode()));
    }

    @Test
    @Order(5)
    void testSearch_withLanguageAndSet() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("q=test&lang=sv&set=retro");

        router.get("/search", ctx, rsp ->
            assertEquals(StatusCode.OK, rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // /qdebug
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void testQdebug_noQuery_rendersDefaultForm() {
        MockContext ctx = new MockContext();

        router.get("/qdebug", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertNonEmptyStringBody(rsp);
        });
    }

    @Test
    @Order(7)
    void testQdebug_withQuery_rendersResults() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("q=test");

        router.get("/qdebug", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertNonEmptyStringBody(rsp);
        });
    }

    @Test
    @Order(8)
    void testQdebug_withRankingParams() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("q=test&domainRankBonus=1.5&bm25b=0.75&temporalBias=NONE");

        router.get("/qdebug", ctx, rsp ->
            assertEquals(StatusCode.OK, rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void assertNonEmptyStringBody(io.jooby.test.MockResponse rsp) {
        assertInstanceOf(String.class, rsp.value(), "Expected String response body");
        assertFalse(((String) rsp.value()).isBlank(), "Response body must not be empty");
    }
}
