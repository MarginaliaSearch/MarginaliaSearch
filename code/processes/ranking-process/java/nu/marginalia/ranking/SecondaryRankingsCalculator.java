package nu.marginalia.ranking;

import com.google.inject.Inject;
import gnu.trove.list.TIntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import nu.marginalia.IndexLocations;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.domainranking.PageRankDomainRanker;
import nu.marginalia.domainranking.accumulator.RankingResultHashSetAccumulator;
import nu.marginalia.process.control.ProcessEventLog;
import nu.marginalia.ranking.data.RankingSearchSet;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/** Recalculates the secondary ranking sets, which act as filters constraining searches
 * to a subset of the indexed domains. */
public class SecondaryRankingsCalculator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final DomainTypes domainTypes;
    private final ProcessEventLog eventLog;
    private final DomainRankingSetsService domainRankingSetsService;
    private final RankingGraphSources graphSources;
    private final Path searchSetsBase;

    private static final String primaryRankingSet = "RANK";

    @Inject
    public SecondaryRankingsCalculator(DomainTypes domainTypes,
                                       RankingGraphSources graphSources,
                                       FileStorageService fileStorageService,
                                       ProcessEventLog eventLog,
                                       DomainRankingSetsService domainRankingSetsService) {
        this.domainTypes = domainTypes;
        this.eventLog = eventLog;
        this.domainRankingSetsService = domainRankingSetsService;
        this.graphSources = graphSources;
        this.searchSetsBase = IndexLocations.getSearchSetsPath(fileStorageService);
    }

    public void calculate() {
        for (var rankingSet : domainRankingSetsService.getAll()) {
            if (primaryRankingSet.equals(rankingSet.name())) { // Skip the primary ranking set
                continue;
            }

            try {
                if (rankingSet.isSpecial()) {
                    switch (rankingSet.name()) {
                        case "BLOGS" -> recalculateSpecialSet(rankingSet, DomainTypes.Type.BLOG);
                        case "SMALL" -> recalculateSpecialSet(rankingSet, DomainTypes.Type.SMALL);
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

        var data = PageRankDomainRanker
                .forDomainNames(graphSources.forDomainList(domains), domains)
                .calculate(rankingSet.depth(), RankingResultHashSetAccumulator::new);

        var set = new RankingSearchSet(rankingSet.name(), rankingSet.fileName(searchSetsBase), data);

        try {
            set.write();
        }
        catch (IOException ex) {
            logger.warn("Failed to write search set", ex);
        }
    }

    private void recalculateSpecialSet(DomainRankingSetsService.DomainRankingSet rankingSet, DomainTypes.Type type) throws SQLException, IOException {
        TIntList knownDomains = domainTypes.getKnownDomainsByType(type);

        var specialSet = new RankingSearchSet(
                rankingSet.name(),
                rankingSet.fileName(searchSetsBase),
                new IntOpenHashSet(knownDomains.toArray()));
        specialSet.write();
    }
}
