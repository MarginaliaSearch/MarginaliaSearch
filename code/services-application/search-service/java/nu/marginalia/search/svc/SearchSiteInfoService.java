package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.api.domains.DomainInfoClient;
import nu.marginalia.api.domains.model.DomainInformation;
import nu.marginalia.api.domains.model.SimilarDomain;
import nu.marginalia.api.livecapture.LiveCaptureClient;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.feedlot.FeedlotClient;
import nu.marginalia.feedlot.model.FeedItems;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.svc.SearchFlagSiteService.FlagSiteFormData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SearchSiteInfoService {
    private static final Logger logger = LoggerFactory.getLogger(SearchSiteInfoService.class);

    private final SearchOperator searchOperator;
    private final DomainInfoClient domainInfoClient;
    private final SearchFlagSiteService flagSiteService;
    private final DbDomainQueries domainQueries;
    private final MustacheRenderer<Object> renderer;
    private final FeedlotClient feedlotClient;
    private final LiveCaptureClient liveCaptureClient;
    private final ScreenshotService screenshotService;

    @Inject
    public SearchSiteInfoService(SearchOperator searchOperator,
                                 DomainInfoClient domainInfoClient,
                                 RendererFactory rendererFactory,
                                 SearchFlagSiteService flagSiteService,
                                 DbDomainQueries domainQueries,
                                 FeedlotClient feedlotClient,
                                 LiveCaptureClient liveCaptureClient,
                                 ScreenshotService screenshotService) throws IOException
    {
        this.searchOperator = searchOperator;
        this.domainInfoClient = domainInfoClient;
        this.flagSiteService = flagSiteService;
        this.domainQueries = domainQueries;

        this.renderer = rendererFactory.renderer("search/site-info/site-info");

        this.feedlotClient = feedlotClient;
        this.liveCaptureClient = liveCaptureClient;
        this.screenshotService = screenshotService;
    }

    public Object handle(Request request, Response response) throws SQLException {
        String domainName = request.params("site");
        String view = request.queryParamOrDefault("view", "info");

        if (null == domainName || domainName.isBlank()) {
            return null;
        }

        var model = switch (view) {
            case "links" -> listLinks(domainName);
            case "docs" -> listDocs(domainName);
            case "info" -> listInfo(domainName);
            case "report" -> reportSite(domainName);
            default -> listInfo(domainName);
        };

        return renderer.render(model);
    }

    public Object handlePost(Request request, Response response) throws SQLException {
        String domainName = request.params("site");
        String view = request.queryParamOrDefault("view", "info");

        if (null == domainName || domainName.isBlank()) {
            return null;
        }

        if (!view.equals("report"))
            return null;

        final int domainId = domainQueries.getDomainId(new EdgeDomain(domainName));

        FlagSiteFormData formData = new FlagSiteFormData(
                domainId,
                request.queryParams("category"),
                request.queryParams("description"),
                request.queryParams("sampleQuery")
        );
        flagSiteService.insertComplaint(formData);

        var complaints = flagSiteService.getExistingComplaints(domainId);

        var model = new ReportDomain(domainName, domainId, complaints, List.of(), true);

        return renderer.render(model);
    }

    private Object reportSite(String domainName) throws SQLException {
        int domainId = domainQueries.getDomainId(new EdgeDomain(domainName));
        var existingComplaints = flagSiteService.getExistingComplaints(domainId);

        return new ReportDomain(domainName,
                domainId,
                existingComplaints,
                flagSiteService.getCategories(),
                false);
    }


    private Backlinks listLinks(String domainName) {
        return new Backlinks(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                searchOperator.doBacklinkSearch(domainName));
    }

    private SiteInfoWithContext listInfo(String domainName) {

        final int domainId = domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1);

        final Future<DomainInformation> domainInfoFuture;
        final Future<List<SimilarDomain>> similarSetFuture;
        final Future<List<SimilarDomain>> linkingDomainsFuture;

        String url = "https://" + domainName + "/";

        boolean hasScreenshot = screenshotService.hasScreenshot(domainId);

        var feedItemsFuture = feedlotClient.getFeedItems(domainName);
        if (domainId < 0) {
            domainInfoFuture = CompletableFuture.failedFuture(new Exception("Unknown Domain ID"));
            similarSetFuture = CompletableFuture.failedFuture(new Exception("Unknown Domain ID"));
            linkingDomainsFuture = CompletableFuture.failedFuture(new Exception("Unknown Domain ID"));
        }
        else if (!domainInfoClient.isAccepting()) {
            domainInfoFuture = CompletableFuture.failedFuture(new Exception("Assistant Service Unavailable"));
            similarSetFuture = CompletableFuture.failedFuture(new Exception("Assistant Service Unavailable"));
            linkingDomainsFuture = CompletableFuture.failedFuture(new Exception("Assistant Service Unavailable"));
        }
        else {
            domainInfoFuture = domainInfoClient.domainInformation(domainId);
            similarSetFuture = domainInfoClient.similarDomains(domainId, 25);
            linkingDomainsFuture = domainInfoClient.linkedDomains(domainId, 25);
        }

        List<UrlDetails> sampleResults = searchOperator.doSiteSearch(domainName, domainId,5);
        if (!sampleResults.isEmpty()) {
            url = sampleResults.getFirst().url.withPathAndParam("/", null).toString();
        }

        FeedItems feedItems = null;
        try {
            feedItems = feedItemsFuture.get();
        } catch (Exception e) {
            logger.debug("Failed to get feed items for {}: {}", domainName, e.getMessage());
        }

        var result = new SiteInfoWithContext(domainName,
                domainId,
                url,
                hasScreenshot,
                waitForFuture(domainInfoFuture, () -> createDummySiteInfo(domainName)),
                waitForFuture(similarSetFuture, List::of),
                waitForFuture(linkingDomainsFuture, List::of),
                feedItems,
                sampleResults
        );

        requestMissingScreenshots(result);

        return result;
    }

    /** Request missing screenshots for the given site info */
    private void requestMissingScreenshots(SiteInfoWithContext result) {

        // Always request the main site screenshot, even if we already have it
        // as this will make the live-capture do a staleness check and update
        // as needed.
        liveCaptureClient.requestScreengrab(result.domainId());

        int requests = 1;

        // Request screenshots for similar and linking domains only if they are absent
        // also throttle the requests to at most 5 per view.

        if (result.similar() != null) {
            for (var similar : result.similar()) {
                if (similar.screenshot()) {
                    continue;
                }
                if (++requests > 5) {
                    break;
                }

                liveCaptureClient.requestScreengrab(similar.domainId());
            }
        }

        if (result.linking() != null) {
            for (var linking : result.linking()) {
                if (linking.screenshot()) {
                    continue;
                }
                if (++requests > 5) {
                    break;
                }

                liveCaptureClient.requestScreengrab(linking.domainId());
            }
        }

    }

    private <T> T waitForFuture(Future<T> future, Supplier<T> fallback) {
        try {
            return future.get(250, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.info("Failed to get domain data: {}", e.getMessage());
            return fallback.get();
        }
    }

    private DomainInformation createDummySiteInfo(String domainName) {
        return DomainInformation.builder()
                    .domain(new EdgeDomain(domainName))
                    .suggestForCrawling(true)
                    .unknownDomain(true)
                    .build();
    }

    private Docs listDocs(String domainName) {
        int domainId = domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1);
        return new Docs(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                searchOperator.doSiteSearch(domainName, domainId, 100));
    }

    public record Docs(Map<String, Boolean> view,
                       String domain,
                       long domainId,
                       List<UrlDetails> results) {
        public Docs(String domain, long domainId, List<UrlDetails> results) {
            this(Map.of("docs", true), domain, domainId, results);
        }

        public String focusDomain() { return domain; }

        public String query() { return "site:" + domain; }

        public boolean isKnown() {
            return domainId > 0;
        }
    }

    public record Backlinks(Map<String, Boolean> view, String domain, long domainId, List<UrlDetails> results) {
        public Backlinks(String domain, long domainId, List<UrlDetails> results) {
            this(Map.of("links", true), domain, domainId, results);
        }

        public String query() { return "links:" + domain; }

        public boolean isKnown() {
            return domainId > 0;
        }
    }

    public record SiteInfoWithContext(Map<String, Boolean> view,
                                      Map<String, Boolean> domainState,
                                      String domain,
                                      int domainId,
                                      String siteUrl,
                                      boolean hasScreenshot,
                                      DomainInformation domainInformation,
                                      List<SimilarDomain> similar,
                                      List<SimilarDomain> linking,
                                      FeedItems feed,
                                      List<UrlDetails> samples
                                      ) {
        public SiteInfoWithContext(String domain,
                                   int domainId,
                                   String siteUrl,
                                   boolean hasScreenshot,
                                   DomainInformation domainInformation,
                                   List<SimilarDomain> similar,
                                   List<SimilarDomain> linking,
                                   FeedItems feedInfo,
                                   List<UrlDetails> samples
                            )
        {
            this(Map.of("info", true),
                    Map.of(domainInfoState(domainInformation), true),
                    domain,
                    domainId,
                    siteUrl,
                    hasScreenshot,
                    domainInformation,
                    similar,
                    linking,
                    feedInfo,
                    samples);
        }

        public String getLayout() {
            // My CSS is too weak to handle this in CSS alone, so I guess we're doing layout in Java...
            if (similar != null && similar.size() < 25) {
                return "lopsided";
            }
            else if (feed != null && !feed.items().isEmpty()) {
                return "lopsided";
            }
            else if (samples != null && !samples.isEmpty()) {
                return "lopsided";
            }
            else {
                return "balanced";
            }
        }

        public String query() { return "site:" + domain; }

        private static String domainInfoState(DomainInformation info) {
            if (info.isBlacklisted()) {
                return "blacklisted";
            }
            if (!info.isUnknownDomain() && info.isSuggestForCrawling()) {
                return "suggestForCrawling";
            }
            if (info.isInCrawlQueue()) {
                return "inCrawlQueue";
            }
            if (info.isUnknownDomain()) {
                return "unknownDomain";
            }
            else {
                return "indexed";
            }
        }

        public boolean isKnown() {
            return domainId > 0;
        }
    }

    public record ReportDomain(
            Map<String, Boolean> view,
            String domain,
            int domainId,
            List<SearchFlagSiteService.FlagSiteComplaintModel> complaints,
            List<SearchFlagSiteService.CategoryItem> category,
            boolean submitted)
    {
        public ReportDomain(String domain,
                            int domainId,
                            List<SearchFlagSiteService.FlagSiteComplaintModel> complaints,
                            List<SearchFlagSiteService.CategoryItem> category,
                            boolean submitted) {
            this(Map.of("report", true), domain, domainId, complaints, category, submitted);
        }

        public String query() { return "site:" + domain; }

        public boolean isKnown() {
            return domainId > 0;
        }
    }

}
