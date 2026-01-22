package nu.marginalia.search;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.StatusCode;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.favicon.FaviconClient;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;

public class SearchService extends JoobyService {

    private final WebsiteUrl websiteUrl;
    private final SearchSiteSubscriptionService siteSubscriptionService;
    private final FaviconClient faviconClient;
    private final DbDomainQueries domainQueries;
    private final SearchFilterService searchFilterService;

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final Histogram wmsa_search_service_request_time = Histogram.builder()
            .name("wmsa_search_service_request_time")
            .classicLinearUpperBounds(0.05, 0.05, 15)
            .labelNames("matchedPath", "method")
            .help("Search service request time (seconds)")
            .register();
    private static final Counter wmsa_search_service_error_count = Counter.builder()
            .name("wmsa_search_service_error_count")
            .labelNames("matchedPath", "method")
            .help("Search service error count")
            .register();

    private final String openSearchXML;

    @Inject
    public SearchService(BaseServiceParams params,
                         WebsiteUrl websiteUrl,
                         SearchFrontPageService frontPageService,
                         SearchAddToCrawlQueueService addToCrawlQueueService,
                         SearchSiteSubscriptionService siteSubscriptionService,
                         SearchSiteInfoService siteInfoService,
                         SearchCrosstalkService crosstalkService,
                         SearchBrowseService searchBrowseService,
                         FaviconClient faviconClient,
                         DbDomainQueries domainQueries,
                         SearchFilterService searchFilterService,
                         SearchQueryService searchQueryService)
    throws Exception {
        super(params,
                ServicePartition.partition(params.configuration.node()),
                List.of(), // No GRPC services
                List.of(new SearchFrontPageService_(frontPageService),
                        new SearchQueryService_(searchQueryService),
                        new SearchSiteInfoService_(siteInfoService),
                        new SearchCrosstalkService_(crosstalkService),
                        new SearchAddToCrawlQueueService_(addToCrawlQueueService),
                        new SearchFilterService_(searchFilterService),
                        new SearchBrowseService_(searchBrowseService)
                ));
        this.websiteUrl = websiteUrl;

        this.siteSubscriptionService = siteSubscriptionService;
        this.faviconClient = faviconClient;
        this.domainQueries = domainQueries;
        this.searchFilterService = searchFilterService;

        try (var is = ClassLoader.getSystemResourceAsStream("static/opensearch.xml")) {
            openSearchXML = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to load OpenSearch XML", e);
        }
    }

    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        final String startTimeAttribute = "start-time";

        jooby.get("/export-opml", siteSubscriptionService::exportOpml);

        jooby.get("/site/https://*", this::handleSiteUrlRedirect);
        jooby.get("/site/http://*", this::handleSiteUrlRedirect);

        jooby.post("/filters/format", searchFilterService::saveFilter);

        jooby.get("/opensearch.xml", ctx -> {
            ctx.setResponseType(MediaType.valueOf("application/opensearchdescription+xml"));
            return openSearchXML;
        });

        String emptySvg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>";
        jooby.get("/site/{domain}/favicon", ctx -> {
            String domain = ctx.path("domain").value();
            try {
                DbDomainQueries.DomainIdWithNode domainIdWithNode = domainQueries.getDomainIdWithNode(new EdgeDomain(domain));
                var faviconMaybe = faviconClient.getFavicon(domain, domainIdWithNode.nodeAffinity());

                if (faviconMaybe.isEmpty()) {
                    ctx.setResponseType(MediaType.valueOf("image/svg+xml"));
                    return emptySvg;
                } else {
                    var favicon = faviconMaybe.get();

                    ctx.responseStream(MediaType.valueOf(favicon.contentType()), consumer -> {
                        consumer.write(favicon.bytes());
                    });
                }
            }
            catch (NoSuchElementException ex) {
                ctx.setResponseType(MediaType.valueOf("image/svg+xml"));
                return emptySvg;
            }
            return "";
        });

        jooby.before((Context ctx) -> {
            ctx.setAttribute(startTimeAttribute, System.nanoTime());
        });

        jooby.after((Context ctx, Object result, Throwable failure) -> {
            if  (failure != null) {
                wmsa_search_service_error_count.labelValues(ctx.getRoute().getPattern(), ctx.getMethod()).inc();
            }
            else {
                Long startTime = ctx.getAttribute(startTimeAttribute);
                if (startTime != null) {
                    wmsa_search_service_request_time.labelValues(ctx.getRoute().getPattern(), ctx.getMethod())
                            .observe((System.nanoTime() - startTime) / 1e9);
                }
            }
        });
    }

    /** Redirect handler for the case when the user passes
     * an url like /site/https://example.com/, in this
     * scenario we want to extract the domain name and redirect
     * to /site/example.com/
     */
    private Context handleSiteUrlRedirect(Context ctx) {
        var pv = ctx.path("*").value();
        int trailSlash = pv.indexOf('/');
        if (trailSlash > 0) {
            pv = pv.substring(0, trailSlash);
        }
        ctx.sendRedirect(StatusCode.TEMPORARY_REDIRECT, websiteUrl.withPath("site/" + pv));
        return ctx;
    }

}
