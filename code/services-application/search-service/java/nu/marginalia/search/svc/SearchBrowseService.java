package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.api.domains.DomainInfoClient;
import nu.marginalia.api.domains.model.SimilarDomain;
import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.browse.model.BrowseResultSet;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.search.results.BrowseResultCleaner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.shuffle;

public class SearchBrowseService {
    private final DbBrowseDomainsRandom randomDomains;
    private final DbDomainQueries domainQueries;
    private final DomainBlacklist blacklist;
    private final DomainInfoClient domainInfoClient;
    private final BrowseResultCleaner browseResultCleaner;

    @Inject
    public SearchBrowseService(DbBrowseDomainsRandom randomDomains,
                               DbDomainQueries domainQueries,
                               DomainBlacklist blacklist,
                               DomainInfoClient domainInfoClient,
                               BrowseResultCleaner browseResultCleaner)
    {
        this.randomDomains = randomDomains;
        this.domainQueries = domainQueries;
        this.blacklist = blacklist;
        this.domainInfoClient = domainInfoClient;
        this.browseResultCleaner = browseResultCleaner;
    }

    public BrowseResultSet getRandomEntries(int set) {
        List<BrowseResult> results = randomDomains.getRandomDomains(25, blacklist, set);

        results.removeIf(browseResultCleaner.shouldRemoveResultPredicateBr());

        return new BrowseResultSet(results);
    }

    public BrowseResultSet getRelatedEntries(String domainName) throws ExecutionException, InterruptedException, TimeoutException {
        var domain = domainQueries.getDomainId(new EdgeDomain(domainName));

        var neighbors = domainInfoClient.similarDomains(domain, 50)
                .get(100, TimeUnit.MILLISECONDS);

        neighbors.removeIf(sd -> !sd.screenshot());

        // If the results are very few, supplement with the alternative shitty algorithm
        if (neighbors.size() < 25) {
            Set<SimilarDomain> allNeighbors = new HashSet<>(neighbors);
            allNeighbors.addAll(domainInfoClient
                    .linkedDomains(domain, 50)
                    .get(100, TimeUnit.MILLISECONDS)
            );

            neighbors.clear();
            neighbors.addAll(allNeighbors);
            neighbors.removeIf(sd -> !sd.screenshot());
        }

        List<BrowseResult> results = new ArrayList<>(neighbors.size());
        for (SimilarDomain sd : neighbors) {
            var resultDomain = domainQueries.getDomain(sd.domainId());
            if (resultDomain.isEmpty())
                continue;

            results.add(new BrowseResult(resultDomain.get().toRootUrl(), sd.domainId(), 0, sd.screenshot()));
        }
        // shuffle the items for a less repetitive experience
        shuffle(neighbors);

        return new BrowseResultSet(results, domainName);
    }
}
