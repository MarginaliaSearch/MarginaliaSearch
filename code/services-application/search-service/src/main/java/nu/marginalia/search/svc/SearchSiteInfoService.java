package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.assistant.client.AssistantClient;
import nu.marginalia.assistant.client.model.SimilarDomain;
import nu.marginalia.client.Context;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.feedlot.model.FeedItems;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.assistant.client.model.DomainInformation;
import nu.marginalia.feedlot.FeedlotClient;
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

public class SearchSiteInfoService {
    private static final Logger logger = LoggerFactory.getLogger(SearchSiteInfoService.class);

    private final SearchOperator searchOperator;
    private final AssistantClient assistantClient;
    private final SearchFlagSiteService flagSiteService;
    private final DbDomainQueries domainQueries;
    private final MustacheRenderer<Object> renderer;
    private final FeedlotClient feedlotClient;

    @Inject
    public SearchSiteInfoService(SearchOperator searchOperator,
                                 AssistantClient assistantClient,
                                 RendererFactory rendererFactory,
                                 SearchFlagSiteService flagSiteService,
                                 DbDomainQueries domainQueries,
                                 FeedlotClient feedlotClient) throws IOException
    {
        this.searchOperator = searchOperator;
        this.assistantClient = assistantClient;
        this.flagSiteService = flagSiteService;
        this.domainQueries = domainQueries;

        this.renderer = rendererFactory.renderer("search/site-info/site-info");

        this.feedlotClient = feedlotClient;
    }

    public Object handle(Request request, Response response) throws SQLException {
        String domainName = request.params("site");
        String view = request.queryParamOrDefault("view", "info");

        response.type("text/html");

        if (null == domainName || domainName.isBlank()) {
            return null;
        }

        var ctx = Context.fromRequest(request);

        var model = switch (view) {
            case "links" -> listLinks(ctx, domainName);
            case "docs" -> listDocs(ctx, domainName);
            case "info" -> listInfo(ctx, domainName);
            case "report" -> reportSite(ctx, domainName);
            default -> listInfo(ctx, domainName);
        };

        return renderer.renderInto(response, model);
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

        return renderer.renderInto(response, model);
    }

    private Object reportSite(Context ctx, String domainName) throws SQLException {
        int domainId = domainQueries.getDomainId(new EdgeDomain(domainName));
        var existingComplaints = flagSiteService.getExistingComplaints(domainId);

        return new ReportDomain(domainName,
                domainId,
                existingComplaints,
                flagSiteService.getCategories(),
                false);
    }


    private Backlinks listLinks(Context ctx, String domainName) {
        return new Backlinks(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                searchOperator.doBacklinkSearch(ctx, domainName));
    }

    private SiteInfoWithContext listInfo(Context ctx, String domainName) {

        final int domainId = domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1);

        final DomainInformation domainInfo;
        final List<SimilarDomain> similarSet;
        final List<SimilarDomain> linkingDomains;
        String url = "https://" + domainName + "/";;

        var feedItemsFuture = feedlotClient.getFeedItems(domainName);
        if (domainId < 0 || !assistantClient.isAccepting()) {
            domainInfo = createDummySiteInfo(domainName);
            similarSet = List.of();
            linkingDomains = List.of();
        }
        else {
            domainInfo = assistantClient.domainInformation(ctx, domainId).blockingFirst();
            similarSet = assistantClient
                    .similarDomains(ctx, domainId, 100)
                    .blockingFirst();
            linkingDomains = assistantClient
                    .linkedDomains(ctx, domainId, 100)
                    .blockingFirst();
        }

        List<UrlDetails> sampleResults = searchOperator.doSiteSearch(ctx, domainName, 5);
        if (!sampleResults.isEmpty()) {
            url = sampleResults.getFirst().url.withPathAndParam("/", null).toString();
        }

        FeedItems feedItems = null;
        try {
            feedItems = feedItemsFuture.get();
        } catch (Exception e) {
            logger.debug("Failed to get feed items for {}: {}", domainName, e.getMessage());
        }

        return new SiteInfoWithContext(domainName,
                domainId,
                url,
                domainInfo,
                similarSet,
                linkingDomains,
                feedItems,
                sampleResults
        );
    }

    private DomainInformation createDummySiteInfo(String domainName) {
        return DomainInformation.builder()
                .domain(new EdgeDomain(domainName))
                .suggestForCrawling(true)
                .unknownDomain(true)
                .build();
    }

    private Docs listDocs(Context ctx, String domainName) {
        return new Docs(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                searchOperator.doSiteSearch(ctx, domainName, 100));
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
                                      long domainId,
                                      String siteUrl,
                                      DomainInformation domainInformation,
                                      List<SimilarDomain> similar,
                                      List<SimilarDomain> linking,
                                      FeedItems feed,
                                      List<UrlDetails> samples
                                      ) {
        public SiteInfoWithContext(String domain,
                                   long domainId,
                                   String siteUrl,
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
                    domainInformation,
                    similar,
                    linking,
                    feedInfo,
                    samples);
        }

        public String getLayout() {
            // My CSS is too weak to handle this in CSS alone, so I guess we're doing layout in Java...
            if (similar.size() < 25) {
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
