package nu.marginalia.search.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import nu.marginalia.index.api.IndexMqClient;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class SearchErrorPageService {
    private final IndexMqClient indexMqClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MustacheRenderer<Object> renderer;

    @Inject
    public SearchErrorPageService(IndexMqClient indexMqClient,
                                  RendererFactory rendererFactory) throws IOException {

        renderer = rendererFactory.renderer("search/error-page-search");

        this.indexMqClient = indexMqClient;
    }

    public String serveError(Context ctx) {
        return renderError(ctx, "Internal error",
                """
                    An error occurred when communicating with the search engine index.
                    <p>
                    This is hopefully a temporary state of affairs.  It may be due to
                    an upgrade.  The index typically takes a about two or three minutes
                    to reload from a cold restart.  Thanks for your patience.
                """);
    }

    private String renderError(Context ctx, String title, String message) {
        return renderer.render(Map.of("title", title, "message", message,
                "profile", ctx.query("profile").value(""),
                "js", ctx.query("js").value(""),
                "query", ctx.query("query").value("")
                ));
    }
}
