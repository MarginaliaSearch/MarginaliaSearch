package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.browse.model.BrowseResultSet;
import nu.marginalia.client.Context;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.svc.SearchBrowseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class BrowseCommand implements SearchCommandInterface {
    private final SearchBrowseService browseService;
    private final MustacheRenderer<BrowseResultSet> browseResultsRenderer;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Predicate<String> queryPatternPredicate = Pattern.compile("^browse:[.A-Za-z\\-0-9:]+$").asPredicate();

    @Inject
    public BrowseCommand(SearchBrowseService browseService,
                         RendererFactory rendererFactory)
            throws IOException
    {
        this.browseService = browseService;

        browseResultsRenderer = rendererFactory.renderer("search/browse-results");
    }

    @Override
    public boolean process(Context ctx, Response response, SearchParameters parameters) {
        if (!queryPatternPredicate.test(parameters.query())) {
            return false;
        }

        var model = browseSite(ctx, parameters.query());

        if (null == model)
            return false;

        browseResultsRenderer.renderInto(response, model,
                        Map.of("query", parameters.query(),
                        "profile", parameters.profileStr(),
                        "focusDomain", model.focusDomain())
        );
        return true;
    }


    private BrowseResultSet browseSite(Context ctx, String humanQuery) {
        String definePrefix = "browse:";
        String word = humanQuery.substring(definePrefix.length()).toLowerCase();

        try {
            if ("random".equals(word)) {
                return browseService.getRandomEntries(0);
            }
            if (word.startsWith("random:")) {
                int set = Integer.parseInt(word.split(":")[1]);
                return browseService.getRandomEntries(set);
            }
            else {
                return browseService.getRelatedEntries(word);
            }
        }
        catch (Exception ex) {
            logger.info("No Results");
            return null;
        }
    }


}
