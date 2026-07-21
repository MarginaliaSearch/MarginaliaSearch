package nu.marginalia.ranking;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import nu.marginalia.IndexLocations;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.domainranking.PageRankDomainRanker;
import nu.marginalia.domainranking.accumulator.RankingResultHashMapAccumulator;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.control.ProcessEventLog;
import nu.marginalia.ranking.data.ConnectivityView;
import nu.marginalia.ranking.data.DomainRankings;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/** Recalculates the primary domain rankings and the connectivity map.  The rankings get
 * baked into the document identifiers during index construction, deciding their sort order
 * within the index, so this needs to run before the indices are reconstructed. */
public class PrimaryRankingsCalculator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final DomainTypes domainTypes;
    private final ProcessEventLog eventLog;
    private final DomainRankingSetsService domainRankingSetsService;
    private final ConnectivitySetsCalculator connectivitySetsCalculator;
    private final DbUpdateRanks dbUpdateRanks;
    private final RankingGraphSources graphSources;
    private final Path searchSetsBase;
    private final int nodeId;

    private static final String primaryRankingSet = "RANK";

    @Inject
    public PrimaryRankingsCalculator(DomainTypes domainTypes,
                                     ProcessConfiguration processConfiguration,
                                     RankingGraphSources graphSources,
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
        this.graphSources = graphSources;
        this.searchSetsBase = IndexLocations.getSearchSetsPath(fileStorageService);
    }

    public void calculate() {
        try {
            reloadDomainsLists();

            ConnectivityView connectivityView = connectivitySetsCalculator.recalculate();

            domainRankingSetsService.get(primaryRankingSet)
                    .ifPresent(rankingSet -> updateMainDomainRankings(rankingSet, connectivityView));

            eventLog.logEvent("RANKING-SET-RECALCULATED", primaryRankingSet);
        } catch (SQLException e) {
            logger.warn("Failed to recalculate primary ranking set", e);
        }
    }

    /** Refresh the special set domain lists from their configured sources.  A failed download
     * leaves the previous list in place. */
    private void reloadDomainsLists() {
        for (var type : List.of(DomainTypes.Type.BLOG, DomainTypes.Type.SMALL)) {
            try {
                domainTypes.reloadDomainsList(type);
            }
            catch (IOException | SQLException ex) {
                logger.warn("Failed to reload domains list for type {}", type, ex);
            }
        }
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

        return PageRankDomainRanker
                .forDomainNames(graphSources.forDomainList(domains), domains)
                .calculate(rankingSet.depth(), () -> new RankingResultHashMapAccumulator(rankingSet.depth()));
    }

    private void saveMainDomainRankings(Int2IntOpenHashMap data) {
        new DomainRankings(data).save(searchSetsBase);
    }
}
