package nu.marginalia.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TIntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.ranking.*;
import nu.marginalia.ranking.accumulator.RankingResultHashMapAccumulator;
import nu.marginalia.ranking.accumulator.RankingResultHashSetAccumulator;
import nu.marginalia.index.svc.searchset.RankingSearchSet;
import nu.marginalia.index.svc.searchset.SearchSetAny;
import nu.marginalia.index.db.DbUpdateRanks;
import nu.marginalia.ranking.data.GraphSource;
import nu.marginalia.ranking.data.LinkGraphSource;
import nu.marginalia.ranking.data.SimilarityGraphSource;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class IndexSearchSetsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DomainTypes domainTypes;
    private final IndexServicesFactory indexServicesFactory;
    private final ServiceEventLog eventLog;
    private final DomainRankingSetsService domainRankingSetsService;
    private final DbUpdateRanks dbUpdateRanks;
    private final GraphSource similarityDomains;
    private final GraphSource linksDomains;

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
                                  LinkGraphSource rankingDomains,
                                  SimilarityGraphSource similarityDomains,
                                  IndexServicesFactory indexServicesFactory,
                                  ServiceEventLog eventLog,
                                  DomainRankingSetsService domainRankingSetsService,
                                  DbUpdateRanks dbUpdateRanks) throws IOException {
        this.nodeId = serviceConfiguration.node();
        this.domainTypes = domainTypes;
        this.indexServicesFactory = indexServicesFactory;
        this.eventLog = eventLog;
        this.domainRankingSetsService = domainRankingSetsService;

        this.dbUpdateRanks = dbUpdateRanks;

        if (similarityDomains.isAvailable()) {
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
                if (rankingSet.isSpecial()) {
                    switch (rankingSet.name()) {
                        case "BLOGS" -> recalculateBlogsSet(rankingSet);
                        case "NONE" -> {} // No-op
                    }
                } else {
                    recalculateNormal(rankingSet);
                }
            }
            catch (Exception ex) {
                logger.warn("Failed to recalculate ranking set {}", rankingSet.name(), ex);
            }
            eventLog.logEvent("RANKING-SET-RECALCULATED", rankingSet.name());
        }
    }

    private void recalculateNormal(DomainRankingSetsService.DomainRankingSet rankingSet) {
        List<String> domains = List.of(rankingSet.domains());

        GraphSource source;

        // Similarity ranking does not behave well with an empty set of domains
        if (domains.isEmpty()) source = linksDomains;
        else source = similarityDomains;

        var data = PageRankDomainRanker
                .forDomainNames(source, domains)
                .calculate(rankingSet.depth(), RankingResultHashSetAccumulator::new);

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
        List<String> domains = List.of(rankingSet.domains());

        final GraphSource source;

        if (domains.isEmpty()) {
            // Similarity ranking does not behave well with an empty set of domains
            source = linksDomains;
        }
        else {
            source = similarityDomains;
        }

        var ranks = PageRankDomainRanker
                        .forDomainNames(source, domains)
                        .calculate(rankingSet.depth(), () -> new RankingResultHashMapAccumulator(rankingSet.depth()));

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
