package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.client.Context;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.DomainInformation;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.siteinfo.DomainInformationService;
import nu.marginalia.search.svc.SearchFlagSiteService.FlagSiteFormData;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class SearchSiteInfoService {

    private final SearchOperator searchOperator;
    private final SimilarDomainsService similarDomains;
    private final DomainInformationService domainInformationService;
    private final SearchFlagSiteService flagSiteService;
    private final DbDomainQueries domainQueries;
    private final MustacheRenderer<Object> renderer;

    @Inject
    public SearchSiteInfoService(SearchOperator searchOperator,
                                 SimilarDomainsService similarDomains,
                                 DomainInformationService domainInformationService,
                                 RendererFactory rendererFactory,
                                 SearchFlagSiteService flagSiteService,
                                 DbDomainQueries domainQueries) throws IOException {
        this.searchOperator = searchOperator;
        this.similarDomains = similarDomains;
        this.domainInformationService = domainInformationService;
        this.flagSiteService = flagSiteService;
        this.domainQueries = domainQueries;

        this.renderer = rendererFactory.renderer("search/site-info/site-info");

    }

    public Object handle(Request request, Response response) throws SQLException {
        String domainName = request.params("site");
        String view = request.queryParamOrDefault("view", "info");

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

    private DomainInformation dummyInformation(String domainName) {
        return DomainInformation.builder()
                .domain(new EdgeDomain(domainName))
                .suggestForCrawling(true)
                .unknownDomain(true)
                .build();
    }

    private Backlinks listLinks(Context ctx, String domainName) {
        return new Backlinks(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                searchOperator.doBacklinkSearch(ctx, domainName));
    }

    private SiteInfoWithContext listInfo(Context ctx, String domainName) {

        final int domainId = domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1);

        final DomainInformation domainInfo = domainInformationService.domainInfo(domainName)
                .orElseGet(() -> dummyInformation(domainName));

        final List<SimilarDomainsService.SimilarDomain> similarSet =
                similarDomains.getSimilarDomains(domainId, 100);
        final List<SimilarDomainsService.SimilarDomain> linkingDomains =
                similarDomains.getLinkingDomains(domainId, 100);

        return new SiteInfoWithContext(domainName,
                domainId,
                domainInfo,
                similarSet,
                linkingDomains
        );
    }
    private Docs listDocs(Context ctx, String domainName) {
        return new Docs(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                searchOperator.doSiteSearch(ctx, domainName));
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
                                      DomainInformation domainInformation,
                                      List<SimilarDomainsService.SimilarDomain> similar,
                                      List<SimilarDomainsService.SimilarDomain> linking) {
        public SiteInfoWithContext(String domain,
                                   long domainId,
                                   DomainInformation domainInformation,
                                   List<SimilarDomainsService.SimilarDomain> similar,
                                   List<SimilarDomainsService.SimilarDomain> linking
                            )
        {
            this(Map.of("info", true),
                    Map.of(domainInfoState(domainInformation), true),
                    domain,
                    domainId,
                    domainInformation,
                    similar,
                    linking);
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
