package nu.marginalia.search.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.browse.model.BrowseResultSet;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.Collections;

@Singleton
public class IndexCommand {

    private final MustacheRenderer<BrowseResultSet> template;
    @Inject
    public IndexCommand(RendererFactory rendererFactory) throws IOException {

        template = rendererFactory.renderer("search/index");
    }

    public String render(Request request, Response response) {
        response.header("Cache-control", "public,max-age=3600");

        return template.render(new BrowseResultSet(Collections.emptyList()));
    }
}
