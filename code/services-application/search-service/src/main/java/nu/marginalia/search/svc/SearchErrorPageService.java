package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.client.Context;
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

    public void serveError(Context ctx, Request request, Response rsp) {
        boolean isIndexUp = indexClient.isAlive();

        try {
            if (!isIndexUp) {
                rsp.body(renderError(request, "The index is down",
                        """
                            The search index server appears to be down.
                            <p>
                            The server was possibly restarted to bring online some changes.
                            Restarting the index typically takes a few minutes, during which
                            searches can't be served.
                        """));
            } else if (indexClient.isBlocked(ctx).blockingFirst()) {
                rsp.body(renderError(request, "The index is starting up",
                        """
                            The search index server appears to be in the process of starting up.
                            This typically takes a few minutes. Be patient.
                        """));
            }
            else {
                rsp.body(renderError(request, "Error processing request",
                        """
                            The search index appears to be up and running, so the problem may be related
                            to some wider general error, or pertain to an error handling your query.
                        """));
            }
        }
        catch (Exception ex) {
            logger.warn("Error during rendering of error page", ex);
            rsp.body(renderError(request, "Error processing error",
                    """
                        An error has occurred, additionally, an error occurred while handling that error
                    """));
        }
    }

    private String renderError(Request request, String title, String message) {
        return renderer.render(Map.of("title", title, "message", message,
                "profile", request.queryParamOrDefault("profile", ""),
                "js", request.queryParamOrDefault("js", ""),
                "query", request.queryParamOrDefault("query", "")
                ));
    }
}
