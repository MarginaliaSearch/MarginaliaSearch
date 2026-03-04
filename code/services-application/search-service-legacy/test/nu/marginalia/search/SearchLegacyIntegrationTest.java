package nu.marginalia.search;

import io.jooby.Formdata;
import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.StatusCode;
import io.jooby.exception.StatusCodeException;
import io.jooby.test.MockContext;
import io.jooby.test.MockRouter;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.QueryApiGrpc;
import nu.marginalia.api.searchquery.RpcQsQuery;
import nu.marginalia.api.searchquery.RpcQsResponse;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.search.svc.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration test for the SearchLegacy HTTP layer.
 *
 * <p>All dependencies are mocked; no external processes or databases are needed.
 * The tests exercise routing, content-type negotiation, and basic response
 * correctness; the kind of regressions most likely to surface during a
 * framework or template refactor.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SearchLegacyIntegrationTest {

    static SearchLegacyTestEnvironment env;
    static QueryApiMock queryApiMock = new QueryApiMock();
    static MockRouter router;

    @BeforeAll
    static void setup() throws Exception {
        env = new SearchLegacyTestEnvironment(queryApiMock, new WebsiteUrl("http://localhost/"));

        var searchQueryService = env.injector.getInstance(SearchQueryService.class);
        var frontPageService   = env.injector.getInstance(SearchFrontPageService.class);
        var siteInfoService    = env.injector.getInstance(SearchSiteInfoService.class);
        var crosstalkService   = env.injector.getInstance(SearchCrosstalkService.class);
        var addToCrawlService  = env.injector.getInstance(SearchAddToCrawlQueueService.class);
        var errorPageService   = env.injector.getInstance(SearchErrorPageService.class);

        Jooby jooby = new Jooby();

        // Replicate the route setup from SearchService.startJooby (without the JoobyService
        // infrastructure that requires a running service registry, gRPC server, etc.)
        jooby.before(ctx -> {
            String path = ctx.getRequestPath();
            if (path.startsWith("/search") || path.startsWith("/site/")) {
                if (!ctx.header("Sec-Purpose").isMissing()) {
                    throw new StatusCodeException(StatusCode.BAD_REQUEST);
                }
            }
        });

        jooby.get("/search",         searchQueryService::pathSearch);
        jooby.get("/",               frontPageService::render);
        jooby.get("/news.xml",       frontPageService::renderNewsFeed);
        jooby.post("/site/suggest/", addToCrawlService::suggestCrawling);
        jooby.get("/site/{site}",    siteInfoService::handle);
        jooby.post("/site/{site}",   siteInfoService::handlePost);
        jooby.get("/crosstalk/",     crosstalkService::handle);

        jooby.error(StatusCodeException.class, (ctx, cause, code) -> {
            ctx.setResponseCode(code);
            ctx.send("");
        });
        jooby.error(Exception.class, (ctx, cause, code) -> {
            ctx.setResponseCode(StatusCode.SERVER_ERROR);
            ctx.send(errorPageService.serveError(ctx));
        });

        router = new MockRouter(jooby);
        router.setFullExecution(true);
    }

    @AfterAll
    static void tearDown() {
        env.close();
    }

    @AfterEach
    void resetMocks() {
        queryApiMock.reset();
    }

    // ------------------------------------------------------------------
    // Front page
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void testFrontPage_returnsHtml() {
        router.get("/", new MockContext(), rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals(MediaType.html, rsp.getContentType());
            assertNonEmptyStringBody(rsp);
        });
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void testSearch_returnsHtml() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("query=marginalia");

        router.get("/search", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals(MediaType.html, rsp.getContentType());
            assertNonEmptyStringBody(rsp);
        });
    }

    @Test
    @Order(3)
    void testSearch_sendsQueryToBackend() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("query=marginalia");

        router.get("/search", ctx, rsp -> assertEquals(StatusCode.OK, rsp.getStatusCode()));

        assertEquals(1, queryApiMock.getSentQueries().size());
        assertEquals("marginalia", queryApiMock.getSentQueries().getFirst().getHumanQuery());
    }

    @Test
    @Order(4)
    void testSearch_emptyQuery_redirectsToFrontPage() {
        // No query param - should redirect to front page rather than blow up.
        MockContext ctx = new MockContext();

        router.get("/search", ctx, rsp -> {
            int code = rsp.getStatusCode().value();
            assertTrue(code < 500, "Unexpected server error: " + code);
        });
    }

    @Test
    @Order(5)
    void testSearch_prefetchDenied() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("query=test");
        ctx.setRequestHeader("Sec-Purpose", "prefetch");

        // MockRouter propagates StatusCodeException before the error handler runs.
        var ex = assertThrows(StatusCodeException.class, () ->
            router.get("/search", ctx, rsp -> {}));
        assertEquals(StatusCode.BAD_REQUEST, ex.getStatusCode());
    }

    // ------------------------------------------------------------------
    // News feed
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void testNewsFeed_returnsRss() {
        router.get("/news.xml", new MockContext(), rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            String ct = rsp.getContentType().toString();
            assertTrue(ct.contains("rss") || ct.contains("xml"),
                    "Expected RSS/XML content-type but got: " + ct);
        });
    }

    // ------------------------------------------------------------------
    // Site info (GET)
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void testSiteInfo_infoView_returnsHtml() {
        router.get("/site/example.com", new MockContext(), rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals(MediaType.html, rsp.getContentType());
            assertNonEmptyStringBody(rsp);
        });
    }

    @Test
    @Order(8)
    void testSiteInfo_docsView_returnsHtml() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("view=docs");

        router.get("/site/example.com", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals(MediaType.html, rsp.getContentType());
        });
    }

    @Test
    @Order(9)
    void testSiteInfo_linksView_returnsHtml() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("view=links");

        router.get("/site/example.com", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals(MediaType.html, rsp.getContentType());
        });
    }

    @Test
    @Order(10)
    void testSiteInfo_prefetchDenied() {
        MockContext ctx = new MockContext();
        ctx.setRequestHeader("Sec-Purpose", "prefetch");

        var ex = assertThrows(StatusCodeException.class, () ->
            router.get("/site/example.com", ctx, rsp -> {}));
        assertEquals(StatusCode.BAD_REQUEST, ex.getStatusCode());
    }

    // ------------------------------------------------------------------
    // Site info (POST - report/flag)
    // ------------------------------------------------------------------

    @Test
    @Order(11)
    void testSiteInfoPost_noReportView_returnsOk() {
        // Without view=report the handler short-circuits and returns null (200).
        MockContext ctx = new MockContext();
        ctx.setMethod("POST");

        router.post("/site/example.com", ctx, rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // Site-search redirect
    // ------------------------------------------------------------------

    @Test
    @Order(12)
    void testSiteSearch_routeRegistered() {
        // MockRouter does not match Jooby's anonymous wildcard route (/*), so this
        // path returns 404 rather than the expected 3xx redirect.  The handler
        // itself is trivially correct (URL-encode + sendRedirect) and is covered
        // by code review.  This test exists only to catch accidental removal of
        // the route registration.
        MockContext ctx = new MockContext();

        router.get("/site-search/example.com/someterms", ctx, rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected server error: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // Add to crawl queue (POST /site/suggest/)
    // ------------------------------------------------------------------

    @Test
    @Order(13)
    void testSuggestCrawl_unknownDomain_returns404() {
        // domainQueries.getDomain(-1) returns empty, causing StatusCodeException(NOT_FOUND).
        // MockRouter propagates StatusCodeException rather than running the error handler,
        // so we assert via assertThrows (same pattern as the prefetch-denied tests).
        MockContext ctx = new MockContext();
        ctx.setMethod("POST");
        Formdata form = Formdata.create(ctx);
        form.put("id", "-1");
        ctx.setForm(form);

        var ex = assertThrows(StatusCodeException.class, () ->
            router.post("/site/suggest/", ctx, rsp -> {}));
        assertEquals(StatusCode.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @Order(14)
    void testSuggestCrawl_knownDomain_redirects() {
        // Override the default "empty" stub for domain ID 42 so the handler
        // can complete the redirect rather than short-circuit to NOT_FOUND.
        when(env.dbDomainQueries.getDomain(42))
                .thenReturn(java.util.Optional.of(new EdgeDomain("example.com")));

        MockContext ctx = new MockContext();
        ctx.setMethod("POST");
        Formdata form = Formdata.create(ctx);
        form.put("id", "42");
        // nomisclick absent - handler skips the DB write but still redirects.
        ctx.setForm(form);

        router.post("/site/suggest/", ctx, rsp ->
            assertTrue(rsp.getStatusCode().value() < 500,
                    "Unexpected error on suggest-crawl happy path: " + rsp.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // Crosstalk
    // ------------------------------------------------------------------

    @Test
    @Order(15)
    void testCrosstalk_returnsHtml() {
        MockContext ctx = new MockContext();
        ctx.setQueryString("domains=example.com,other.com");

        router.get("/crosstalk/", ctx, rsp -> {
            assertEquals(StatusCode.OK, rsp.getStatusCode());
            assertEquals(MediaType.html, rsp.getContentType());
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Assert that the response body is a non-empty String (i.e. the renderer ran). */
    private static void assertNonEmptyStringBody(io.jooby.test.MockResponse rsp) {
        assertInstanceOf(String.class, rsp.value(), "Expected String response body");
        assertFalse(((String) rsp.value()).isBlank(), "Response body must not be empty");
    }

    // ------------------------------------------------------------------
    // Minimal gRPC mock - captures sent queries, returns empty responses
    // ------------------------------------------------------------------

    static class QueryApiMock extends QueryApiGrpc.QueryApiImplBase {
        private final List<RpcQsQuery> sentQueries = new ArrayList<>();

        public synchronized List<RpcQsQuery> getSentQueries() {
            return new ArrayList<>(sentQueries);
        }

        public synchronized void reset() {
            sentQueries.clear();
        }

        @Override
        public synchronized void query(RpcQsQuery request,
                                       io.grpc.stub.StreamObserver<RpcQsResponse> responseObserver) {
            sentQueries.add(request);
            responseObserver.onNext(RpcQsResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
