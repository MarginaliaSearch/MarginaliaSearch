package nu.marginalia.search;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SearchService extends JoobyService {

    private final SearchSiteSubscriptionService siteSubscriptionService;

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final Histogram wmsa_search_service_request_time = Histogram.build()
            .name("wmsa_search_service_request_time")
            .linearBuckets(0.05, 0.05, 15)
            .labelNames("matchedPath", "method")
            .help("Search service request time (seconds)")
            .register();
    private static final Counter wmsa_search_service_error_count = Counter.build()
            .name("wmsa_search_service_error_count")
            .labelNames("matchedPath", "method")
            .help("Search service error count")
            .register();

    @Inject
    public SearchService(BaseServiceParams params,
                         SearchFrontPageService frontPageService,
                         SearchAddToCrawlQueueService addToCrawlQueueService,
                         SearchSiteSubscriptionService siteSubscriptionService,
                         SearchSiteInfoService siteInfoService,
                         SearchCrosstalkService crosstalkService,
                         SearchBrowseService searchBrowseService,
                         SearchQueryService searchQueryService)
    throws Exception {
        super(params,
                ServicePartition.any(),
                List.of(), // No GRPC services
                List.of(new SearchFrontPageService_(frontPageService),
                        new SearchQueryService_(searchQueryService),
                        new SearchSiteInfoService_(siteInfoService),
                        new SearchCrosstalkService_(crosstalkService),
                        new SearchAddToCrawlQueueService_(addToCrawlQueueService),
                        new SearchBrowseService_(searchBrowseService)
                ));

        this.siteSubscriptionService = siteSubscriptionService;
    }

    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        final String startTimeAttribute = "start-time";

        jooby.get("/export-opml", siteSubscriptionService::exportOpml);
        jooby.before((Context ctx) -> {
            ctx.setAttribute(startTimeAttribute, System.nanoTime());
        });

        jooby.after((Context ctx, Object result, Throwable failure) -> {
            if  (failure != null) {
                wmsa_search_service_error_count.labels(ctx.getRoute().getPattern(), ctx.getMethod()).inc();
            }
            else {
                Long startTime = ctx.getAttribute(startTimeAttribute);
                if (startTime != null) {
                    wmsa_search_service_request_time.labels(ctx.getRoute().getPattern(), ctx.getMethod())
                            .observe((System.nanoTime() - startTime) / 1e9);
                }
            }
        });
    }


}
