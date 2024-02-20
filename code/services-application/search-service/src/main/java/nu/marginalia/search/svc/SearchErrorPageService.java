package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.Map;

public class SearchErrorPageService {
    private final IndexClient indexClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MustacheRenderer<Object> renderer;

    @Inject
    public SearchErrorPageService(IndexClient indexClient,
                                  RendererFactory rendererFactory) throws IOException {

        renderer = rendererFactory.renderer("search/error-page-search");

        this.indexClient = indexClient;
    }

    public void serveError(Request request, Response rsp) {
        rsp.body(renderError(request, "Internal error",
                """
                    An error occurred when communicating with the search engine index.
                    <p>
                    This is hopefully a temporary state of affairs.  It may be due to
                    an upgrade.  The index typically takes a about two or three minutes
                    to reload from a cold restart.  Thanks for your patience.
                """));
    }

    private String renderError(Request request, String title, String message) {
        return renderer.render(Map.of("title", title, "message", message,
                "profile", request.queryParamOrDefault("profile", ""),
                "js", request.queryParamOrDefault("js", ""),
                "query", request.queryParamOrDefault("query", "")
                ));
    }
}
