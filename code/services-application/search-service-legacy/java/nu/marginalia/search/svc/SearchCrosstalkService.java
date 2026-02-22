package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.service.server.RateLimiter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeoutException;

public class SearchCrosstalkService {
    private static final Logger logger = LoggerFactory.getLogger(SearchCrosstalkService.class);
    private final SearchOperator searchOperator;
    private final ScrapeStopper scrapeStopper;
    private final MustacheRenderer<CrosstalkResult> renderer;
    private final MustacheRenderer<Object> waitRenderer;

    private final RateLimiter rateLimiter = RateLimiter.queryPerMinuteLimiter(30);

    @Inject
    public SearchCrosstalkService(SearchOperator searchOperator,
                                  ScrapeStopper scrapeStopper,
                                  RendererFactory rendererFactory) throws IOException
    {
        this.searchOperator = searchOperator;
        this.scrapeStopper = scrapeStopper;
        this.renderer = rendererFactory.renderer("search/site-info/site-crosstalk");
        this.waitRenderer = rendererFactory.renderer("search/wait-page");
    }

    public Object handle(Request request, Response response) throws SQLException, TimeoutException {
        String remoteIp = request.headers("X-Forwarded-For");
        String sst = request.queryParamOrDefault("sst", "");
        ScrapeStopper.TokenState tokenState = scrapeStopper.validateToken(sst, remoteIp);

        if (!rateLimiter.isAllowed() && tokenState != ScrapeStopper.TokenState.VALIDATED) {
            if (tokenState == ScrapeStopper.TokenState.INVALID)
                sst = scrapeStopper.getToken("CT", remoteIp, Duration.ofSeconds(3), Duration.ofMinutes(1), 10);

            int waitDuration = (int) scrapeStopper.getRemaining(sst).orElseThrow().toSeconds() + 1;
            Map<String, String> queryParams = new LinkedHashMap<>();
            request.queryParams().forEach(param -> {
                queryParams.put(param, request.queryParams(param));
            });
            queryParams.put("sst", sst);
            StringJoiner redirUrlBuilder = new StringJoiner("&", "?", "");
            queryParams.forEach((k,v) -> {
                redirUrlBuilder.add(k + "=" + v);
            });


            response.header("Cache-Control", "no-store");

            return waitRenderer.render(Map.of(
                    "waitDuration", waitDuration,
                    "redirUrl", redirUrlBuilder.toString()
            ));
        }

        String domains = request.queryParams("domains");
        String[] parts = StringUtils.split(domains, ',');

        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected exactly two domains");
        }

        response.type("text/html");

        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        var resAtoB = searchOperator.doLinkSearch(parts[0], parts[1]);
        var resBtoA = searchOperator.doLinkSearch(parts[1], parts[0]);

        var model = new CrosstalkResult(parts[0], parts[1], resAtoB, resBtoA);

        return renderer.render(model);
    }



    private record CrosstalkResult(String domainA,
                                   String domainB,
                                   List<UrlDetails> forward,
                                   List<UrlDetails> backward)
    {

        public boolean isFocusDomain() {
            return true; // Hack to get the search result templates behave well
        }
        public boolean hasBoth() {
            return !forward.isEmpty() && !backward.isEmpty();
        }

    }
}
