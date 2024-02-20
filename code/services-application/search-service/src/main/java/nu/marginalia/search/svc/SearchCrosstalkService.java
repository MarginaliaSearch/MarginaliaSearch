package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.UrlDetails;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class SearchCrosstalkService {
    private static final Logger logger = LoggerFactory.getLogger(SearchCrosstalkService.class);
    private final SearchOperator searchOperator;
    private final MustacheRenderer<CrosstalkResult> renderer;

    @Inject
    public SearchCrosstalkService(SearchOperator searchOperator,
                                  RendererFactory rendererFactory) throws IOException
    {
        this.searchOperator = searchOperator;
        this.renderer = rendererFactory.renderer("search/site-info/site-crosstalk");
    }

    public Object handle(Request request, Response response) throws SQLException {
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
