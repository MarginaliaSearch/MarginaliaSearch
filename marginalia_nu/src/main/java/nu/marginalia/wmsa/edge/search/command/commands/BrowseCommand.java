package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.search.command.SearchCommandInterface;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;
import nu.marginalia.wmsa.edge.search.model.BrowseResultSet;
import nu.marginalia.wmsa.edge.search.results.BrowseResultCleaner;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class BrowseCommand implements SearchCommandInterface {
    private final EdgeDataStoreDao edgeDataStoreDao;
    private final ScreenshotService screenshotService;
    private final EdgeDomainBlacklist blacklist;
    private final MustacheRenderer<BrowseResultSet> browseResultsRenderer;
    private final BrowseResultCleaner browseResultCleaner;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Predicate<String> queryPatternPredicate = Pattern.compile("^browse:[.A-Za-z\\-0-9:]+$").asPredicate();

    @Inject
    public BrowseCommand(EdgeDataStoreDao edgeDataStoreDao,
                         ScreenshotService screenshotService,
                         EdgeDomainBlacklist blacklist,
                         RendererFactory rendererFactory,
                         BrowseResultCleaner browseResultCleaner)
            throws IOException
    {
        this.edgeDataStoreDao = edgeDataStoreDao;
        this.screenshotService = screenshotService;
        this.blacklist = blacklist;
        this.browseResultCleaner = browseResultCleaner;

        browseResultsRenderer = rendererFactory.renderer("edge/browse-results");
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        if (!queryPatternPredicate.test(query)) {
            return Optional.empty();
        }

        return Optional.ofNullable(browseSite(ctx, query))
                .map(results -> browseResultsRenderer.render(results, Map.of("query", query, "profile", parameters.profileStr())));
    }


    private BrowseResultSet browseSite(Context ctx, String humanQuery) {
        String definePrefix = "browse:";
        String word = humanQuery.substring(definePrefix.length()).toLowerCase();

        Set<String> domainHashes = new HashSet<>();

        try {
            if ("random".equals(word)) {
                var results = edgeDataStoreDao.getRandomDomains(25, blacklist, 0);

                results.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

                return new BrowseResultSet(results);
            }
            if (word.startsWith("random:")) {
                int set = Integer.parseInt(word.split(":")[1]);

                var results = edgeDataStoreDao.getRandomDomains(25, blacklist, set);

                results.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

                return new BrowseResultSet(results);
            }
            else {
                var domain = edgeDataStoreDao.getDomainId(new EdgeDomain(word));
                var neighbors = edgeDataStoreDao.getDomainNeighborsAdjacent(domain, blacklist, 45);

                neighbors.removeIf(browseResultCleaner.shouldRemoveResultPredicate());
                neighbors.sort(Comparator.comparing(BrowseResult::relatedness).reversed());

                return new BrowseResultSet(neighbors);
            }
        }
        catch (Exception ex) {
            logger.info("No Results");
            return null;
        }
    }

}
