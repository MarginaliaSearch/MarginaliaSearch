package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.JteRenderer;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.SearchErrorMessageModel;
import nu.marginalia.search.model.SearchFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.Map;

public class SearchErrorPageService {
    private final WebsiteUrl websiteUrl;
    private final JteRenderer jteRenderer;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchErrorPageService(WebsiteUrl websiteUrl,
                                  JteRenderer jteRenderer) throws IOException {
        this.websiteUrl = websiteUrl;
        this.jteRenderer = jteRenderer;
    }

    public void serveError(Request request, Response rsp) {

        var params = SearchParameters.forRequest(
                request.queryParamOrDefault("query", ""),
                websiteUrl,
                request);


        rsp.body(jteRenderer.render("serp/error.jte",
                Map.of("navbar", NavbarModel.LIMBO,
                        "model", new SearchErrorMessageModel(
                                "An error occurred when communicating with the search engine index.",
                                """
                                            This is hopefully a temporary state of affairs.  It may be due to
                                            an upgrade.  The index typically takes a about two or three minutes
                                            to reload from a cold restart.  Thanks for your patience.
                                            """,
                                params,
                                new SearchFilters(params)
                        )
                    )
                ));
    }

}
