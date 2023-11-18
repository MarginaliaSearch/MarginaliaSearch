package nu.marginalia.search.svc;
import com.google.inject.Inject;
import nu.marginalia.client.Context;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.DomainInformation;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.siteinfo.DomainInformationService;
import nu.marginalia.search.svc.SearchFlagSiteService.FlagSiteFormData;
import spark.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class SearchSiteInfoService {

    private final SearchOperator searchOperator;
    private final DomainInformationService domainInformationService;
    private final SearchFlagSiteService flagSiteService;
    private final DbDomainQueries domainQueries;
    private final MustacheRenderer<Object> renderer;

    @Inject
    public SearchSiteInfoService(SearchOperator searchOperator,
                                 DomainInformationService domainInformationService,
                                 RendererFactory rendererFactory,
                                 SearchFlagSiteService flagSiteService,
                                 DbDomainQueries domainQueries) throws IOException {
        this.searchOperator = searchOperator;
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
            case "info" -> siteInfo(ctx, domainName);
            case "report" -> reportSite(ctx, domainName);
            default -> siteInfo(ctx, domainName);
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

    private SiteInfo siteInfo(Context ctx, String domainName) {
        OptionalInt id = domainQueries.tryGetDomainId(new EdgeDomain(domainName));

        if (id.isEmpty()) {
            return new SiteInfo(domainName, -1, null, dummyInformation(domainName));
        }

        String screenshotPath = "/screenshot/"+id.getAsInt();
        DomainInformation domainInfo = domainInformationService
                .domainInfo(domainName)
                .orElseGet(() -> dummyInformation(domainName));

        return new SiteInfo(domainName, id.getAsInt(), screenshotPath, domainInfo);
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

    private Docs listDocs(Context ctx, String domainName) {
        return new Docs(domainName,
                domainQueries.tryGetDomainId(new EdgeDomain(domainName)).orElse(-1),
                searchOperator.doSiteSearch(ctx, domainName));
    }

    public record SiteInfo(Map<String, Boolean> view,
                           Map<String, Boolean> domainState,
                           long domainId,
                           String domain,
                           @Nullable String screenshotUrl,
                           DomainInformation domainInformation)
    {
        public SiteInfo(String domain,
                        long domainId,
                        @Nullable String screenshotUrl,
                        DomainInformation domainInformation)
        {
            this(Map.of("info", true),
                 Map.of(domainInfoState(domainInformation), true),
                 domainId,
                 domain,
                 screenshotUrl,
                 domainInformation);
        }

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

        public String query() { return "site:" + domain; }

        public boolean isKnown() {
            return domainId > 0;
        }
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
