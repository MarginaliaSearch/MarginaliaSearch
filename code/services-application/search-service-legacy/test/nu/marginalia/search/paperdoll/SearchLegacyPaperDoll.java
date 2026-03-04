package nu.marginalia.search.paperdoll;

import io.grpc.stub.StreamObserver;
import io.jooby.ExecutionMode;
import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.ServerOptions;
import io.jooby.StatusCode;
import io.jooby.exception.StatusCodeException;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.QueryApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.RpcQsQuery;
import nu.marginalia.api.searchquery.RpcQsResponse;
import nu.marginalia.api.searchquery.RpcQsResultPagination;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcRawResultItem;
import nu.marginalia.search.SearchLegacyTestEnvironment;
import nu.marginalia.search.svc.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("paperdoll")
public class SearchLegacyPaperDoll {

    @Test
    void start() throws Exception {
        if (!Boolean.getBoolean("runPaperDoll")) {
            return;
        }

        try (var env = new SearchLegacyTestEnvironment(
                new RichQueryApiMock(),
                new WebsiteUrl("http://localhost:9999/")))
        {
            var searchQueryService = env.injector.getInstance(SearchQueryService.class);
            var frontPageService   = env.injector.getInstance(SearchFrontPageService.class);
            var siteInfoService    = env.injector.getInstance(SearchSiteInfoService.class);
            var crosstalkService   = env.injector.getInstance(SearchCrosstalkService.class);
            var addToCrawlService  = env.injector.getInstance(SearchAddToCrawlQueueService.class);
            var errorPageService   = env.injector.getInstance(SearchErrorPageService.class);

            env.injector.getInstance(nu.marginalia.service.server.Initialization.class).setReady();

            // Run Jooby in a background thread - Jooby.runApp() blocks that thread
            // via Thread.currentThread().join(), which doesn't work reliably in
            // Gradle's test runner context. Instead we start the server in a daemon
            // thread and block this thread explicitly.
            Thread serverThread = new Thread(() ->
                Jooby.runApp(new String[]{"application.env=prod"}, ExecutionMode.WORKER, () -> new Jooby() {{
                    setServerOptions(new ServerOptions().setPort(9999));

                    // Serve static assets from the classpath static/ directory.
                    // JoobyService's filesystem route only works in Docker; this
                    // classpath form covers the dev/test environment.
                    assets("/*", "/static");

                    before(ctx -> {
                        String path = ctx.getRequestPath();
                        if (path.startsWith("/search") || path.startsWith("/site/")) {
                            if (!ctx.header("Sec-Purpose").isMissing()) {
                                throw new StatusCodeException(StatusCode.BAD_REQUEST);
                            }
                        }
                    });

                    get("/search",         searchQueryService::pathSearch);
                    get("/",               frontPageService::render);
                    get("/news.xml",       frontPageService::renderNewsFeed);
                    post("/site/suggest/", addToCrawlService::suggestCrawling);
                    get("/site/{site}",    siteInfoService::handle);
                    post("/site/{site}",   siteInfoService::handlePost);
                    get("/crosstalk/",     crosstalkService::handle);

                    error(Exception.class, (ctx, cause, code) -> {
                        ctx.setResponseType(MediaType.html);
                        ctx.send(errorPageService.serveError(ctx));
                    });
                }}),
                "jooby-paperdoll"
            );
            serverThread.setDaemon(false);
            serverThread.start();

            System.out.println("PaperDoll server running at http://localhost:9999/");
            System.out.println("Try: http://localhost:9999/ (front page)");
            System.out.println("     http://localhost:9999/search?query=marginalia (search results)");
            System.out.println("     http://localhost:9999/site/example.com (site info)");
            System.out.println("     http://localhost:9999/crosstalk/?domains=example.com,other.com (crosstalk)");
            System.out.println("Press Ctrl+C to stop.");

            //noinspection InfiniteLoopStatement
            for (;;) Thread.sleep(60_000);
        }
    }

    // ------------------------------------------------------------------
    // Rich gRPC mock - returns realistic-looking search results
    // ------------------------------------------------------------------

    static class RichQueryApiMock extends QueryApiGrpc.QueryApiImplBase {
        private static final String[] TITLES = {
            "The Small Web Manifesto",
            "Marginalia Search: Finding the Indie Web",
            "Personal Homepages Are Not Dead",
            "Why Plain Text Is Better",
            "A Guide to the Blogosphere",
        };
        private static final String[] DESCRIPTIONS = {
            "A declaration of principles for a smaller, more human internet free from tracking and algorithmic feeds.",
            "Marginalia Search is an experimental search engine focused on non-commercial content from the indie web.",
            "Personal homepages have been a fixture of the internet since the early days. Here is why they still matter.",
            "Plain text has survived every technology wave since the dawn of computing. Here is why it will outlast HTML.",
            "The blogosphere is alive and well. Thousands of independent writers publish every day outside of social media.",
        };
        private static final String[] URLS = {
            "https://example.com/manifesto",
            "https://marginalia.nu/log/",
            "https://personal.example.org/homepage",
            "https://plaintext.example.com/essay",
            "https://blogosphere.example.net/guide",
        };

        @Override
        public void query(RpcQsQuery request, StreamObserver<RpcQsResponse> responseObserver) {
            responseObserver.onNext(buildResponse());
            responseObserver.onCompleted();
        }

        private RpcQsResponse buildResponse() {
            var specs = RpcIndexQuery.newBuilder()
                    .setQueryLimits(RpcQueryLimits.newBuilder()
                            .setResultsTotal(100)
                            .setResultsByDomain(5)
                            .setTimeoutMs(250)
                            .build())
                    .build();

            var pagination = RpcQsResultPagination.newBuilder()
                    .setPage(1)
                    .setPageSize(25)
                    .setTotalResults(TITLES.length)
                    .build();

            var builder = RpcQsResponse.newBuilder()
                    .setSpecs(specs)
                    .setPagination(pagination)
                    .addSearchTermsHuman("example query");

            for (int i = 0; i < TITLES.length; i++) {
                builder.addResults(buildResult(i));
            }

            return builder.build();
        }

        private RpcDecoratedResultItem buildResult(int i) {
            return RpcDecoratedResultItem.newBuilder()
                    .setRawItem(RpcRawResultItem.newBuilder()
                            .setCombinedId(i + 1L)
                            .setEncodedDocMetadata(0L)
                            .setHtmlFeatures(0)
                            .build())
                    .setUrl(URLS[i % URLS.length])
                    .setTitle(TITLES[i % TITLES.length])
                    .setDescription(DESCRIPTIONS[i % DESCRIPTIONS.length])
                    .setFormat("HTML5")
                    .setFeatures(0)
                    .setRankingScore(1.0 + i * 0.5)
                    .setResultsFromDomain(1)
                    .setBestPositions(0L)
                    .build();
        }
    }
}
