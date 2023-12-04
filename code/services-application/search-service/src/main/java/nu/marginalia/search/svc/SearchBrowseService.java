package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.browse.DbBrowseDomainsSimilarCosine;
import nu.marginalia.browse.DbBrowseDomainsSimilarOldAlgo;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.browse.model.BrowseResultSet;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.search.results.BrowseResultCleaner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.shuffle;

public class SearchBrowseService {
    private final DbBrowseDomainsRandom randomDomains;
    private final DbBrowseDomainsSimilarCosine similarDomains;
    private final DbBrowseDomainsSimilarOldAlgo similarDomainsOld;
    private final DbDomainQueries domainQueries;
    private final DomainBlacklist blacklist;
    private final BrowseResultCleaner browseResultCleaner;

    @Inject
    public SearchBrowseService(DbBrowseDomainsRandom randomDomains,
                               DbBrowseDomainsSimilarCosine similarDomains,
                               DbBrowseDomainsSimilarOldAlgo similarDomainsOld,
                               DbDomainQueries domainQueries,
                               DomainBlacklist blacklist,
                               BrowseResultCleaner browseResultCleaner)
    {
        this.randomDomains = randomDomains;
        this.similarDomains = similarDomains;
        this.similarDomainsOld = similarDomainsOld;
        this.domainQueries = domainQueries;
        this.blacklist = blacklist;
        this.browseResultCleaner = browseResultCleaner;
    }

    public BrowseResultSet getRandomEntries(int set) {
        List<BrowseResult> results = randomDomains.getRandomDomains(25, blacklist, set);

        results.removeIf(browseResultCleaner.shouldRemoveResultPredicate());

        return new BrowseResultSet(results);
    }

    public BrowseResultSet getRelatedEntries(String word) {
        var domain = domainQueries.getDomainId(new EdgeDomain(word));

        var neighbors = similarDomains.getDomainNeighborsAdjacentCosineRequireScreenshot(domain, blacklist, 256);
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
