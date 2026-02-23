package nu.marginalia.search.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.QueryParam;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.JteRenderer;
import nu.marginalia.search.ScrapeStopperInterceptor;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.SimpleSearchResults;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.service.server.RateLimiter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;

public class SearchCrosstalkService {
    private static final Logger logger = LoggerFactory.getLogger(SearchCrosstalkService.class);

    private final SearchOperator searchOperator;
    private final ScrapeStopperInterceptor scrapeStopperInterceptor;

    private final RateLimiter rateLimiter = RateLimiter.queryPerMinuteLimiter(15);

    @Inject
    public SearchCrosstalkService(SearchOperator searchOperator,
                                  ScrapeStopperInterceptor scrapeStopperInterceptor
                                  ) throws IOException
    {
        this.searchOperator = searchOperator;
        this.scrapeStopperInterceptor = scrapeStopperInterceptor;
    }

    @GET
    @Path("/crosstalk")
    public ModelAndView<?> crosstalk(Context context,
                                     @QueryParam String domains
                                     ) throws SQLException, TimeoutException {

        var interceptResult = scrapeStopperInterceptor.intercept("CT", domains, rateLimiter, context);

        if (interceptResult instanceof ScrapeStopperInterceptor.InterceptRedirect redir) {
            return new MapModelAndView("siteinfo/ctwait.jte",
                    Map.of("model",
                            new CrosstalkWait(domains, redir.sst(), redir.waitTime()),
                            "navbar", NavbarModel.SITEINFO)
            );
        }
        if (interceptResult instanceof ScrapeStopperInterceptor.InterceptPrefetch prefetch) {
            throw new NoSuchElementException();
        }

        String[] parts = StringUtils.split(domains, ',');

        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected exactly two domains");
        }

        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        SimpleSearchResults resAtoB = searchOperator.doLinkSearch(parts[0], parts[1]);
        SimpleSearchResults resBtoA = searchOperator.doLinkSearch(parts[1], parts[0]);

        CrosstalkResult model = new CrosstalkResult(parts[0], parts[1], resAtoB.results, resBtoA.results);

        return new MapModelAndView(
                "siteinfo/crosstalk.jte",
                Map.of("model", model,
                        "navbar", NavbarModel.SITEINFO));
    }



    public record CrosstalkResult(String domainA,
                                   String domainB,
                                   List<UrlDetails> aToB,
                                   List<UrlDetails> bToA)
    {

        public boolean hasBoth() {
            return !aToB.isEmpty() && !bToA.isEmpty();
        }
        public boolean hasA() {
            return !aToB.isEmpty();
        }
        public boolean hasB() {
            return !bToA.isEmpty();
        }
    }

    public record CrosstalkWait(String domains,
                                String sst,
                                Duration waitTime)
    {
        public String domainA() {
            return domains.split(",")[0];
        }

        public String domainB() {
            return domains.split(",")[1];
        }

        public String redirUrl() {
            return String.format("?domains=%s&sst=%s", domains,sst);
        }
    }
}
