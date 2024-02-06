package nu.marginalia.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TIntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.ranking.RankingAlgorithm;
import nu.marginalia.ranking.ReversePageRank;
import nu.marginalia.ranking.StandardPageRank;
import nu.marginalia.ranking.accumulator.RankingResultHashMapAccumulator;
import nu.marginalia.ranking.accumulator.RankingResultHashSetAccumulator;
import nu.marginalia.ranking.data.RankingDomainFetcher;
import nu.marginalia.ranking.data.RankingDomainFetcherForSimilarityData;
import nu.marginalia.index.svc.searchset.RankingSearchSet;
import nu.marginalia.index.svc.searchset.SearchSetAny;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.index.db.DbUpdateRanks;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class IndexSearchSetsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DomainTypes domainTypes;
    private final ServiceHeartbeat heartbeat;
    private final IndexServicesFactory indexServicesFactory;
    private final ServiceEventLog eventLog;
    private final DomainRankingSetsService domainRankingSetsService;
    private final DbUpdateRanks dbUpdateRanks;
    private final RankingDomainFetcher similarityDomains;
    private final RankingDomainFetcher linksDomains;

    private final ConcurrentHashMap<String, SearchSet> rankingSets = new ConcurrentHashMap<>();
    // Below are binary indices that are used to constrain a search
    private final SearchSet anySet = new SearchSetAny();
    private final int nodeId;

    // The ranking value of the domains used in sorting the domains
    private volatile DomainRankings domainRankings = new DomainRankings();

    private static final String primaryRankingSet = "RANK";

    @Inject
    public IndexSearchSetsService(DomainTypes domainTypes,
                                  ServiceConfiguration serviceConfiguration,
                                  ServiceHeartbeat heartbeat,
                                  RankingDomainFetcher rankingDomains,
                                  RankingDomainFetcherForSimilarityData similarityDomains,
                                  IndexServicesFactory indexServicesFactory,
                                  ServiceEventLog eventLog,
                                  DomainRankingSetsService domainRankingSetsService,
                                  DbUpdateRanks dbUpdateRanks) throws IOException {
        this.nodeId = serviceConfiguration.node();
        this.domainTypes = domainTypes;
        this.heartbeat = heartbeat;
        this.indexServicesFactory = indexServicesFactory;
        this.eventLog = eventLog;
        this.domainRankingSetsService = domainRankingSetsService;

        this.dbUpdateRanks = dbUpdateRanks;

        if (similarityDomains.hasData()) {
            this.similarityDomains = similarityDomains;
            this.linksDomains = rankingDomains;
        }
        else {
            // on test environments the cosine similarity graph may not be present
            logger.info("Domain similarity is not present, falling back on link graph");
            this.similarityDomains = rankingDomains;
            this.linksDomains = rankingDomains;
        }

        for (var rankingSet : domainRankingSetsService.getAll()) {
            rankingSets.put(rankingSet.name(),
                    new RankingSearchSet(rankingSet.name(),
                            rankingSet.fileName(indexServicesFactory.getSearchSetsBase())
                    )
            );
        }
    }

    public DomainRankings getDomainRankings() {
        return domainRankings;
    }

    public SearchSet getSearchSetByName(String searchSetIdentifier) {

        if (null == searchSetIdentifier) {
            return anySet;
        }

        if ("NONE".equals(searchSetIdentifier) || "".equals(searchSetIdentifier)) {
            return anySet;
        }

        return Objects.requireNonNull(rankingSets.get(searchSetIdentifier), "Unknown search set");
    }

    /** Recalculates the primary ranking set.  This gets baked into the identifiers in the index, effectively
     * changing their sort order, so it's important to run this before reconstructing the indices. */
    public void recalculatePrimaryRank() {
        try {
            domainRankingSetsService.get(primaryRankingSet).ifPresent(this::updateDomainRankings);
            eventLog.logEvent("RANKING-SET-RECALCULATED", primaryRankingSet);
        } catch (SQLException e) {
            logger.warn("Failed to primary ranking set", e);
        }
    }

    public void recalculateSecondary() {
        for (var rankingSet : domainRankingSetsService.getAll()) {
            if (primaryRankingSet.equals(rankingSet.name())) { // Skip the primary ranking set
                continue;
            }

            try {
                if (DomainRankingSetsService.DomainSetAlgorithm.SPECIAL.equals(rankingSet.algorithm())) {
                    switch (rankingSet.name()) {
                        case "BLOGS" -> recalculateBlogsSet(rankingSet);
                        case "NONE" -> {} // No-op
                    }
                } else {
                    recalculateNornal(rankingSet);
                }
            }
            catch (Exception ex) {
                logger.warn("Failed to recalculate ranking set {}", rankingSet.name(), ex);
            }
            eventLog.logEvent("RANKING-SET-RECALCULATED", rankingSet.name());
        }
    }

    private void recalculateNornal(DomainRankingSetsService.DomainRankingSet rankingSet) {
        String[] domains = rankingSet.domains();

        RankingAlgorithm rankingAlgorithm = switch (rankingSet.algorithm()) {
            case LINKS_PAGERANK -> new StandardPageRank(linksDomains, domains);
            case LINKS_CHEIRANK -> new ReversePageRank(linksDomains, domains);
            case ADJACENCY_PAGERANK -> new StandardPageRank(similarityDomains, domains);
            case ADJACENCY_CHEIRANK -> new ReversePageRank(similarityDomains, domains);
            default -> throw new IllegalStateException("Unexpected value: " + rankingSet.algorithm());
        };

        var data = rankingAlgorithm.pageRankWithPeripheralNodes(rankingSet.depth(), RankingResultHashSetAccumulator::new);

        var set = new RankingSearchSet(rankingSet.name(), rankingSet.fileName(indexServicesFactory.getSearchSetsBase()), data);
        rankingSets.put(rankingSet.name(), set);

        try {
            set.write();
        }
        catch (IOException ex) {
            logger.warn("Failed to write search set", ex);
        }
    }



    private void recalculateBlogsSet(DomainRankingSetsService.DomainRankingSet rankingSet) throws SQLException, IOException {
        TIntList knownDomains = domainTypes.getKnownDomainsByType(DomainTypes.Type.BLOG);

        if (knownDomains.isEmpty()) {
            // FIXME: We don't want to reload the entire list every time, but we do want to do it sometimes. Actor maybe?
            domainTypes.reloadDomainsList(DomainTypes.Type.BLOG);
            knownDomains = domainTypes.getKnownDomainsByType(DomainTypes.Type.BLOG);
        }

        synchronized (this) {
            var blogSet = new RankingSearchSet(rankingSet.name(), rankingSet.fileName(indexServicesFactory.getSearchSetsBase()), new IntOpenHashSet(knownDomains.toArray()));
            rankingSets.put(rankingSet.name(), blogSet);
            blogSet.write();
        }
    }

    private void updateDomainRankings(DomainRankingSetsService.DomainRankingSet rankingSet) {

        var spr = new StandardPageRank(similarityDomains, rankingSet.domains());
        var ranks = spr.pageRankWithPeripheralNodes(rankingSet.depth(), () -> new RankingResultHashMapAccumulator(rankingSet.depth()));

        synchronized (this) {
            domainRankings = new DomainRankings(ranks);
        }

        domainRankings.save(indexServicesFactory.getSearchSetsBase());

        if (nodeId == 1) {
            // The EC_DOMAIN table has a field that reflects the rank, this needs to be set for search result ordering to
            // make sense, but only do this on the primary node to avoid excessive db locks
            dbUpdateRanks.execute(ranks);
        }
    }

}
