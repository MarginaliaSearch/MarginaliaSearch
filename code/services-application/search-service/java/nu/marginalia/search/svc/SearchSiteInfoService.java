package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import io.jooby.annotation.*;
import nu.marginalia.api.domains.DomainInfoClient;
import nu.marginalia.api.domains.model.DomainInformation;
import nu.marginalia.api.domains.model.SimilarDomain;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.api.feeds.RpcFeed;
import nu.marginalia.api.feeds.RpcFeedItem;
import nu.marginalia.api.livecapture.LiveCaptureClient;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.GroupedUrlDetails;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.ResultsPage;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.svc.SearchFlagSiteService.FlagSiteFormData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SearchSiteInfoService {
    private static final Logger logger = LoggerFactory.getLogger(SearchSiteInfoService.class);

    private final SearchOperator searchOperator;
    private final DomainInfoClient domainInfoClient;
    private final SearchFlagSiteService flagSiteService;
    private final DbDomainQueries domainQueries;
    private final FeedsClient feedsClient;
    private final LiveCaptureClient liveCaptureClient;
    private final ScreenshotService screenshotService;

    private final HikariDataSource dataSource;
    private final SearchSiteSubscriptionService searchSiteSubscriptions;

    @Inject
    public SearchSiteInfoService(SearchOperator searchOperator,
                                 DomainInfoClient domainInfoClient,
                                 SearchFlagSiteService flagSiteService,
                                 DbDomainQueries domainQueries,
                                 FeedsClient feedsClient,
                                 LiveCaptureClient liveCaptureClient,
                                 ScreenshotService screenshotService,
                                 HikariDataSource dataSource,
                                 SearchSiteSubscriptionService searchSiteSubscriptions)
    {
        this.searchOperator = searchOperator;
        this.domainInfoClient = domainInfoClient;
        this.flagSiteService = flagSiteService;
        this.domainQueries = domainQueries;

        this.feedsClient = feedsClient;
        this.liveCaptureClient = liveCaptureClient;
        this.screenshotService = screenshotService;
        this.dataSource = dataSource;
        this.searchSiteSubscriptions = searchSiteSubscriptions;

        Thread.ofPlatform().name("Recently Added Domains Model Updater").start(this::modelUpdater);
    }

    private volatile SiteOverviewModel cachedOverviewModel = new SiteOverviewModel(List.of());

    @GET
    @Path("/site")
    public ModelAndView<?> handleOverview(@QueryParam String domain) {
        if (domain != null) {
            // redirect to /site/domainName
            return new MapModelAndView("redirect.jte", Map.of("url", "/site/"+domain));
        }

        return new MapModelAndView("siteinfo/start.jte",
                Map.of("navbar", NavbarModel.SITEINFO,
                        "model", cachedOverviewModel));
    }

    private void modelUpdater() {
        while (!Thread.interrupted()) {
            List<SiteOverviewModel.DiscoveredDomain> domains = new ArrayList<>();

            // This query can be quite expensive, so we can't run it on demand
            // for every request. Instead, we run it every 15 minutes and cache
            // the result.

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement("""
                    SELECT DOMAIN_NAME, DISCOVER_DATE
                    FROM EC_DOMAIN
                    WHERE NODE_AFFINITY = 0
                    ORDER BY ID DESC
                    LIMIT 10
                    """))
            {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    domains.add(new SiteOverviewModel.DiscoveredDomain(
                            rs.getString("DOMAIN_NAME"),
                            rs.getString("DISCOVER_DATE"))
                    );
                }
            } catch (SQLException ex) {
                logger.warn("Failed to get recently added domains: {}", ex.getMessage());
            }

            cachedOverviewModel = new SiteOverviewModel(domains);

            try {
                TimeUnit.MINUTES.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public record SiteOverviewModel(List<DiscoveredDomain> domains) {
        public record DiscoveredDomain(String name, String timestamp) {}
    }

    @GET
    @Path("/site/{domainName}")
    public ModelAndView<?>  handle(
            Context context,
            @PathParam String domainName,
            @QueryParam String view,
            @QueryParam Integer page
    ) throws SQLException {

        if (null == domainName || domainName.isBlank()) {
            return null;
        }

        page = Objects.requireNonNullElse(page, 1);
        view = Objects.requireNonNullElse(view, "info");

        SiteInfoModel model = switch (view) {
            case "links" -> listLinks(domainName, page);
            case "docs" -> listDocs(domainName, page);
            case "info" -> listInfo(context, domainName);
            case "report" -> reportSite(domainName);
            default -> listInfo(context, domainName);
        };

        return new MapModelAndView("siteinfo/main.jte",
                Map.of("model", model, "navbar", NavbarModel.SITEINFO));
    }

    @POST
    @Path("/site/{domainName}/subscribe")
    public ModelAndView<?> toggleSubscription(Context context, @PathParam String domainName) throws SQLException {
        searchSiteSubscriptions.toggleSubscription(context, new EdgeDomain(domainName));

        return new MapModelAndView("redirect.jte", Map.of("url", "/site/"+domainName));
    }

    @POST
    @Path("/site/{domainName}")
    public ModelAndView<?> handleComplaint(
            @PathParam String domainName,
            @QueryParam String view,
            @FormParam String category,
            @FormParam String description,
            @FormParam String samplequery

    ) throws SQLException {

        if (null == domainName || domainName.isBlank()) {
            return null;
        }

        if (!view.equals("report"))
            return null;

        final int domainId = domainQueries.getDomainId(new EdgeDomain(domainName));

        FlagSiteFormData formData = new FlagSiteFormData(
                domainId,
                category,
                description,
                samplequery
        );
        flagSiteService.insertComplaint(formData);

        var complaints = flagSiteService.getExistingComplaints(domainId);

        var model = new ReportDomain(domainName, domainId, complaints, List.of(), true);

        return new MapModelAndView("siteinfo/main.jte",
                Map.of("model", model, "navbar", NavbarModel.SITEINFO));
    }

    private ReportDomain reportSite(String domainName) throws SQLException {
        int domainId = domainQueries.getDomainId(new EdgeDomain(domainName));
        var existingComplaints = flagSiteService.getExistingComplaints(domainId);

        return new ReportDomain(domainName,
                domainId,
                existingComplaints,
                flagSiteService.getCategories(),
                false);
    }


    private Backlinks listLinks(String domainName, int page) {
        var results = searchOperator.doBacklinkSearch(domainName, page);
        return new Backlinks(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                GroupedUrlDetails.groupResults(results.results),
                results.resultPages
        );
    }

    private SiteInfoWithContext listInfo(Context context, String domainName) throws ExecutionException {

        var domain = new EdgeDomain(domainName);
        final int domainId = domainQueries.tryGetDomainId(domain).orElse(-1);

        final Future<DomainInformation> domainInfoFuture;
        final Future<List<SimilarDomain>> similarSetFuture;
        final Future<List<SimilarDomain>> linkingDomainsFuture;
        final CompletableFuture<RpcFeed> feedItemsFuture;
        String url = "https://" + domainName + "/";

        boolean hasScreenshot = screenshotService.hasScreenshot(domainId);
        boolean isSubscribed = searchSiteSubscriptions.isSubscribed(context, domain);

        if (domainId < 0) {
            domainInfoFuture = CompletableFuture.failedFuture(new Exception("Unknown Domain ID"));
            similarSetFuture = CompletableFuture.failedFuture(new Exception("Unknown Domain ID"));
            linkingDomainsFuture = CompletableFuture.failedFuture(new Exception("Unknown Domain ID"));
            feedItemsFuture = CompletableFuture.failedFuture(new Exception("Unknown Domain ID"));
        }
        else if (!domainInfoClient.isAccepting()) {
            domainInfoFuture = CompletableFuture.failedFuture(new Exception("Assistant Service Unavailable"));
            similarSetFuture = CompletableFuture.failedFuture(new Exception("Assistant Service Unavailable"));
            linkingDomainsFuture = CompletableFuture.failedFuture(new Exception("Assistant Service Unavailable"));
            feedItemsFuture = CompletableFuture.failedFuture(new Exception("Assistant Service Unavailable"));
        }
        else {
            domainInfoFuture = domainInfoClient.domainInformation(domainId);
            similarSetFuture = domainInfoClient.similarDomains(domainId, 25);
            linkingDomainsFuture = domainInfoClient.linkedDomains(domainId, 25);
            feedItemsFuture = feedsClient.getFeed(domainId);
        }

        List<UrlDetails> sampleResults = searchOperator.doSiteSearch(domainName, domainId,5, 1).results;
        if (!sampleResults.isEmpty()) {
            url = sampleResults.getFirst().url.withPathAndParam("/", null).toString();
        }


        var result = new SiteInfoWithContext(domainName,
                isSubscribed,
                domainQueries.otherSubdomains(domain, 5),
                domainId,
                url,
                hasScreenshot,
                waitForFuture(domainInfoFuture, () -> createDummySiteInfo(domainName)),
                waitForFuture(similarSetFuture, List::of),
                waitForFuture(linkingDomainsFuture, List::of),
                waitForFuture(feedItemsFuture.thenApply(FeedItems::new), () -> FeedItems.dummyValue(domainName)),
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

    private Docs listDocs(String domainName, int page) {
        int domainId = domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1);
        var results = searchOperator.doSiteSearch(domainName, domainId, 100, page);

        return new Docs(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                results.results.stream().sorted(Comparator.comparing(deets -> -deets.topology)).toList(),
                results.resultPages
                );
    }

    public record Docs(String domain,
                       long domainId,
                       List<UrlDetails> results,
                       List<ResultsPage> pages) implements SiteInfoModel  {

        public String focusDomain() { return domain; }

        public String query() { return "site:" + domain; }

        public boolean isKnown() {
            return domainId > 0;
        }
    }

    public record Backlinks(String domain,
                            long domainId,
                            List<GroupedUrlDetails> results,
                            List<ResultsPage> pages
                            ) implements SiteInfoModel
    {
        public String query() { return "links:" + domain; }

        public boolean isKnown() {
            return domainId > 0;
        }
    }

    public interface SiteInfoModel {
        String domain();
    }

    public record SiteInfoWithContext(String domain,
                                      boolean isSubscribed,
                                      List<DbDomainQueries.DomainWithNode> siblingDomains,
                                      int domainId,
                                      String siteUrl,
                                      boolean hasScreenshot,
                                      DomainInformation domainInformation,
                                      List<SimilarDomain> similar,
                                      List<SimilarDomain> linking,
                                      FeedItems feed,
                                      List<UrlDetails> samples)
            implements SiteInfoModel
    {

        public boolean hasSamples() {
            return samples != null && !samples.isEmpty();
        }

        public boolean hasFeed() {
            return feed != null && !feed.items.isEmpty();
        }

        public String query() { return "site:" + domain; }

        public boolean isKnown() {
            return domainId > 0;
        }
    }

    public record FeedItem(String title, String date, String description, String url) {

        public FeedItem(String domain, RpcFeedItem rpcFeedItem) {
            this(rpcFeedItem.getTitle(),
                    rpcFeedItem.getDate(),
                    rpcFeedItem.getDescription(),
                    absoluteFeedUrl(domain, rpcFeedItem.getUrl())
            );
        }


        private static String absoluteFeedUrl(String domain, String url) {
            if (url.startsWith("/")) { // relative URL
                url = "https://" + domain + url;
            } else if (!url.contains(":")) { // no schema, assume relative URL
                url = "https://" + domain + "/" + url;
            }

            return url;
        }

        public String pubDay() { // Extract the date from an ISO style date string
            if (date.length() > 10) {
                return date.substring(0, 10);
            }
            return date;
        }

        public String descriptionSafe() {
            return description
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }

    public record FeedItems(String domain, String feedUrl, String updated, List<FeedItem> items) {

        public static FeedItems dummyValue(String domain) {
            return new FeedItems(domain, "", "", List.of());
        }

        public FeedItems(RpcFeed rpcFeedItems) {
            this(rpcFeedItems.getDomain(),
                    rpcFeedItems.getFeedUrl(),
                    rpcFeedItems.getUpdated(),
                    rpcFeedItems.getItemsList().stream().map(item -> new FeedItem(rpcFeedItems.getDomain(), item)).toList());
        }

    }

    public record ReportDomain(
            String domain,
            int domainId,
            List<SearchFlagSiteService.FlagSiteComplaintModel> complaints,
            List<SearchFlagSiteService.CategoryItem> category,
            boolean submitted) implements SiteInfoModel
    {
        public String query() { return "site:" + domain; }

        public boolean isKnown() {
            return domainId > 0;
        }
    }

}
