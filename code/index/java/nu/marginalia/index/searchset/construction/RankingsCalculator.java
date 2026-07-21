package nu.marginalia.index.searchset.construction;

import com.google.inject.Inject;
import gnu.trove.list.TIntList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import nu.marginalia.IndexLocations;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.domaingraph.GraphSource;
import nu.marginalia.domaingraph.LinkGraphSource;
import nu.marginalia.domaingraph.SimilarityGraphSource;
import nu.marginalia.domainranking.PageRankDomainRanker;
import nu.marginalia.domainranking.accumulator.RankingResultHashMapAccumulator;
import nu.marginalia.domainranking.accumulator.RankingResultHashSetAccumulator;
import nu.marginalia.index.searchset.DbUpdateRanks;
import nu.marginalia.index.searchset.DomainRankings;
import nu.marginalia.index.searchset.RankingSearchSet;
import nu.marginalia.index.searchset.connectivity.ConnectivityView;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.control.ProcessEventLog;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

public class RankingsCalculator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final DomainTypes domainTypes;
    private final ProcessEventLog eventLog;
    private final DomainRankingSetsService domainRankingSetsService;
    private final ConnectivitySetsCalculator connectivitySetsCalculator;
    private final DbUpdateRanks dbUpdateRanks;
    private final GraphSource similarityDomains;
    private final GraphSource linksDomains;
    private final Path searchSetsBase;
    private final int nodeId;

    private static final String primaryRankingSet = "RANK";

    @Inject
    public RankingsCalculator(DomainTypes domainTypes,
                              ProcessConfiguration processConfiguration,
                              LinkGraphSource rankingDomains,
                              SimilarityGraphSource similarityDomains,
                              FileStorageService fileStorageService,
                              ProcessEventLog eventLog,
                              DomainRankingSetsService domainRankingSetsService,
                              ConnectivitySetsCalculator connectivitySetsCalculator,
                              DbUpdateRanks dbUpdateRanks) {
        this.nodeId = processConfiguration.node();
        this.domainTypes = domainTypes;
        this.eventLog = eventLog;
        this.domainRankingSetsService = domainRankingSetsService;
        this.connectivitySetsCalculator = connectivitySetsCalculator;
        this.dbUpdateRanks = dbUpdateRanks;
        this.searchSetsBase = IndexLocations.getSearchSetsPath(fileStorageService);

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
    }

    /** Recalculates the primary ranking set.  This gets baked into the identifiers in the index, effectively
     * changing their sort order, so it's important to run this _before_ reconstructing the indices. */
    public void calculatePrimary() {
        try {
            ConnectivityView connectivityView = connectivitySetsCalculator.recalculate();

            domainRankingSetsService.get(primaryRankingSet)
                    .ifPresent(rankingSet -> updateMainDomainRankings(rankingSet, connectivityView));

            eventLog.logEvent("RANKING-SET-RECALCULATED", primaryRankingSet);
        } catch (SQLException e) {
            logger.warn("Failed to primary ranking set", e);
        }
    }

    public void calculateSecondary() {
        for (var rankingSet : domainRankingSetsService.getAll()) {
            if (primaryRankingSet.equals(rankingSet.name())) { // Skip the primary ranking set
                continue;
            }

            try {
                if (rankingSet.isSpecial()) {
                    switch (rankingSet.name()) {
                        case "BLOGS" -> recalculateSpecialSetSet(rankingSet, DomainTypes.Type.BLOG);
                        case "SMALL" -> recalculateSpecialSetSet(rankingSet, DomainTypes.Type.SMALL);
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

        var set = new RankingSearchSet(rankingSet.name(), rankingSet.fileName(searchSetsBase), data);

        try {
            set.write();
        }
        catch (IOException ex) {
            logger.warn("Failed to write search set", ex);
        }
    }

    private void recalculateSpecialSetSet(DomainRankingSetsService.DomainRankingSet rankingSet, DomainTypes.Type type) throws SQLException, IOException {
        TIntList knownDomains = domainTypes.getKnownDomainsByType(type);

        if (knownDomains.isEmpty()) {
            // FIXME: We don't want to reload the entire list every time, but we do want to do it sometimes. Actor maybe?
            domainTypes.reloadDomainsList(type);
            knownDomains = domainTypes.getKnownDomainsByType(type);
        }

        var specialSet = new RankingSearchSet(
                rankingSet.name(),
                rankingSet.fileName(searchSetsBase),
                new IntOpenHashSet(knownDomains.toArray()));
        specialSet.write();
    }

    private void updateMainDomainRankings(DomainRankingSetsService.DomainRankingSet rankingSet,
                                          ConnectivityView connectivityView) {
        if (!connectivityView.isEmpty()) {
            // If connectivity data is available, use it for ranking as well

            var connectivityData = connectivityView.emulateRankData();
            saveMainDomainRankings(connectivityData);

            if (nodeId == 1) {
                // The EC_DOMAIN table has a field that reflects the rank, this needs to be set for search result ordering to
                // make sense, but only do this on the primary node to avoid excessive db locks

                var pageRankData = getMainDomainRankings(rankingSet);
                dbUpdateRanks.execute(pageRankData);
            }
        }
        else {
            // Connectivity unavailable, use pagerank-style ranking

            var pageRankData = getMainDomainRankings(rankingSet);
            saveMainDomainRankings(pageRankData);

            if (nodeId == 1) {
                // The EC_DOMAIN table has a field that reflects the rank, this needs to be set for search result ordering to
                // make sense, but only do this on the primary node to avoid excessive db locks
                dbUpdateRanks.execute(pageRankData);
            }
        }
    }

    private Int2IntOpenHashMap getMainDomainRankings(DomainRankingSetsService.DomainRankingSet rankingSet) {
        List<String> domains = List.of(rankingSet.domains());

        final GraphSource source;

        if (domains.isEmpty()) {
            // Similarity ranking does not behave well with an empty set of domains
            source = linksDomains;
        } else {
            source = similarityDomains;
        }

        return PageRankDomainRanker
                .forDomainNames(source, domains)
                .calculate(rankingSet.depth(), () -> new RankingResultHashMapAccumulator(rankingSet.depth()));
    }

    private void saveMainDomainRankings(Int2IntOpenHashMap data) {
        new DomainRankings(data).save(searchSetsBase);
    }
}
