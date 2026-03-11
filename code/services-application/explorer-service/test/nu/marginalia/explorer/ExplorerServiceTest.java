package nu.marginalia.explorer;

import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.test.MockContext;
import io.jooby.test.MockRouter;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * Tests for the explorer-service.
 *
 * <p>Route integration tests use inline handlers because ExplorerService extends
 * JoobyService, whose constructor requires heavy infrastructure (service registry,
 * message queue, gRPC) that cannot be mocked cheaply. The unit tests for
 * trimUrlJunk exercise real production code directly.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExplorerServiceTest {

    static MockRouter router;

    @SuppressWarnings("unchecked")
    @BeforeAll
    static void setup() throws Exception {
        var rendererFactory = mock(RendererFactory.class);
        var explorerRenderer = mock(MustacheRenderer.class);
        when(rendererFactory.renderer("explorer/explorer")).thenReturn(explorerRenderer);
        when(explorerRenderer.render(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (arg instanceof ExplorerService.SearchResults sr) {
                return "<html>query=" + sr.query() + " results=" + sr.resultList().size() + "</html>";
            }
            return "<html>explorer</html>";
        });

        // Build a Jooby app replicating ExplorerService.startJooby route registration.
        // We cannot call real startJooby() because it requires JoobyService infrastructure.
        Jooby jooby = new Jooby();
        jooby.get("/", ctx -> explorerRenderer.render(
                new ExplorerService.SearchResults("", "", null, Collections.emptyList())));
        jooby.get("/search", ctx -> {
            String query = ctx.query("domain").valueOrNull();
            if (query == null) {
                return explorerRenderer.render(
                        new ExplorerService.SearchResults("", "No domain specified", null, Collections.emptyList()));
            }
            return explorerRenderer.render(
                    new ExplorerService.SearchResults(query, "", null, Collections.emptyList()));
        });

        router = new MockRouter(jooby);
        router.setFullExecution(true);
    }

    // ------------------------------------------------------------------
    // Route tests
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void testIndex_returnsOk() {
        router.get("/", new MockContext(), rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertNonEmptyStringBody(rsp);
        });
    }

    @Test
    @Order(2)
    void testSearch_withDomain_returnsOk() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("domain=example.com");

        router.get("/search", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertNonEmptyStringBody(rsp);
        });
    }

    @Test
    @Order(3)
    void testSearch_noDomain_returnsOk() {
        MockContext ctx = new MockContext();

        router.get("/search", ctx, rsp ->
            assertEquals(StatusCode.OK, rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // Unit tests for trimUrlJunk (real production code)
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void testTrimUrlJunk_plainDomain() {
        assertEquals("example.com", ExplorerService.trimUrlJunk("example.com"));
    }

    @Test
    @Order(11)
    void testTrimUrlJunk_httpPrefix() {
        assertEquals("example.com", ExplorerService.trimUrlJunk("http://example.com"));
    }

    @Test
    @Order(12)
    void testTrimUrlJunk_httpsPrefix() {
        assertEquals("example.com", ExplorerService.trimUrlJunk("https://example.com"));
    }

    @Test
    @Order(13)
    void testTrimUrlJunk_withPath() {
        assertEquals("example.com", ExplorerService.trimUrlJunk("http://example.com/some/path"));
    }

    @Test
    @Order(14)
    void testTrimUrlJunk_httpsWithPath() {
        assertEquals("example.com", ExplorerService.trimUrlJunk("https://example.com/page"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void assertNonEmptyStringBody(io.jooby.test.MockResponse rsp) {
        assertInstanceOf(String.class, rsp.value(), "Expected String response body");
        assertFalse(((String) rsp.value()).isBlank(), "Response body must not be empty");
    }
}
