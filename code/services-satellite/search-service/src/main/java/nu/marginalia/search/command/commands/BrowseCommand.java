package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.browse.DbBrowseDomainsSimilarCosine;
import nu.marginalia.browse.DbBrowseDomainsSimilarOldAlgo;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.browse.model.BrowseResultSet;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.results.BrowseResultCleaner;
import nu.marginalia.client.Context;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.util.Collections.shuffle;

public class BrowseCommand implements SearchCommandInterface {
    private final DbBrowseDomainsRandom randomDomains;
    private final DbBrowseDomainsSimilarCosine similarDomains;
    private final DbBrowseDomainsSimilarOldAlgo similarDomainsOld;
    private final DbDomainQueries domainQueries;
    private final DomainBlacklist blacklist;
    private final MustacheRenderer<BrowseResultSet> browseResultsRenderer;
    private final BrowseResultCleaner browseResultCleaner;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Predicate<String> queryPatternPredicate = Pattern.compile("^browse:[.A-Za-z\\-0-9:]+$").asPredicate();

    @Inject
    public BrowseCommand(DbBrowseDomainsRandom randomDomains,
                         DbBrowseDomainsSimilarCosine similarDomains,
                         DbBrowseDomainsSimilarOldAlgo similarDomainsOld, DbDomainQueries domainQueries,
                         DomainBlacklist blacklist,
                         RendererFactory rendererFactory,
                         BrowseResultCleaner browseResultCleaner)
            throws IOException
    {
        this.randomDomains = randomDomains;
        this.similarDomains = similarDomains;
        this.similarDomainsOld = similarDomainsOld;
        this.domainQueries = domainQueries;
        this.blacklist = blacklist;
        this.browseResultCleaner = browseResultCleaner;

        browseResultsRenderer = rendererFactory.renderer("search/browse-results");
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        if (!queryPatternPredicate.test(query)) {
            return Optional.empty();
        }

        return Optional.ofNullable(browseSite(ctx, query))
                .map(results -> browseResultsRenderer.render(results,
                        Map.of("query", query,
                        "profile", parameters.profileStr(),
                        "focusDomain", results.focusDomain())));
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
        List<BrowseResult> results = randomDomains.getRandomDomains(25, blacklist, set);

        results.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

        return new BrowseResultSet(results);
    }

    private BrowseResultSet getRelatedEntries(String word) {
        var domain = domainQueries.getDomainId(new EdgeDomain(word));

        var neighbors = similarDomains.getDomainNeighborsAdjacentCosine(domain, blacklist, 256);
        neighbors.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

        // If the results are very few, supplement with the alternative shitty algorithm
        if (neighbors.size() < 25) {
            Set<BrowseResult> allNeighbors = new HashSet<>(neighbors);
            allNeighbors.addAll(similarDomainsOld.getDomainNeighborsAdjacent(domain, blacklist, 50));

            neighbors.clear();
            neighbors.addAll(allNeighbors);
            neighbors.removeIf(browseResultCleaner.shouldRemoveResultPredicate());
        }

        // shuffle the items for a less repetitive experience
        shuffle(neighbors);

        return new BrowseResultSet(neighbors, word);
    }

}
