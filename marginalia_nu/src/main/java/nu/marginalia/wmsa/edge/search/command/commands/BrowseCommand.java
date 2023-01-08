package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDomainBlacklist;
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
    private final EdgeDomainBlacklist blacklist;
    private final MustacheRenderer<BrowseResultSet> browseResultsRenderer;
    private final BrowseResultCleaner browseResultCleaner;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Predicate<String> queryPatternPredicate = Pattern.compile("^browse:[.A-Za-z\\-0-9:]+$").asPredicate();

    @Inject
    public BrowseCommand(EdgeDataStoreDao edgeDataStoreDao,
                         EdgeDomainBlacklist blacklist,
                         RendererFactory rendererFactory,
                         BrowseResultCleaner browseResultCleaner)
            throws IOException
    {
        this.edgeDataStoreDao = edgeDataStoreDao;
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

        try {
            if ("random".equals(word)) {
                return getRandomEntries(0);
            }
            if (word.startsWith("random:")) {
                int set = Integer.parseInt(word.split(":")[1]);
                return getRandomEntries(set);
            }
            else {
                return getRelatedEntries(word);
            }
        }
        catch (Exception ex) {
            logger.info("No Results");
            return null;
        }
    }

    private BrowseResultSet getRandomEntries(int set) {
        var results = edgeDataStoreDao.getRandomDomains(25, blacklist, set);

        results.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

        return new BrowseResultSet(results);
    }

    private BrowseResultSet getRelatedEntries(String word) {
        var domain = edgeDataStoreDao.getDomainId(new EdgeDomain(word));

        var neighbors = edgeDataStoreDao.getDomainNeighborsAdjacentCosine(domain, blacklist, 256);
        neighbors.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

        // If the results are very few, supplement with the alternative shitty algorithm
        if (neighbors.size() < 25) {
            Set<BrowseResult> allNeighbors = new HashSet<>(neighbors);
            allNeighbors.addAll(edgeDataStoreDao.getDomainNeighborsAdjacent(domain, blacklist, 50));

            neighbors.clear();
            neighbors.addAll(allNeighbors);
            neighbors.removeIf(browseResultCleaner.shouldRemoveResultPredicate());
        }

        neighbors.sort(Comparator.comparing(BrowseResult::relatedness).reversed());

        return new BrowseResultSet(neighbors);
    }

}
