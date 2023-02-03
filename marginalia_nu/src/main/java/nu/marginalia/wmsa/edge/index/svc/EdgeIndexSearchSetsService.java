package nu.marginalia.wmsa.edge.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.ranking.ReversePageRank;
import nu.marginalia.wmsa.edge.index.ranking.StandardPageRank;
import nu.marginalia.wmsa.edge.index.ranking.RankingDomainFetcher;
import nu.marginalia.wmsa.edge.index.ranking.accumulator.RankingResultBitSetAccumulator;
import nu.marginalia.wmsa.edge.index.ranking.accumulator.RankingResultHashMapAccumulator;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.model.RankingSettings;
import nu.marginalia.wmsa.edge.index.postings.DomainRankings;
import nu.marginalia.wmsa.edge.index.svc.searchset.RankingSearchSet;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSet;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetAny;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetIdentifier;

import java.io.IOException;

@Singleton
public class EdgeIndexSearchSetsService {
    private final RankingDomainFetcher rankingDomains;
    private final RankingSettings rankingSettings;
    private final SearchSet anySet = new SearchSetAny();
    private volatile RankingSearchSet retroSet;
    private volatile RankingSearchSet smallWebSet;
    private volatile RankingSearchSet academiaSet;

    private volatile DomainRankings domainRankings = new DomainRankings();

    @Inject
    public EdgeIndexSearchSetsService(RankingDomainFetcher rankingDomains,
                                      RankingSettings rankingSettings,
                                      IndexServicesFactory servicesFactory) throws IOException {
        this.rankingDomains = rankingDomains;
        this.rankingSettings = rankingSettings;

        smallWebSet = new RankingSearchSet(SearchSetIdentifier.SMALLWEB, servicesFactory.getSearchSetsBase().resolve("small-web.dat"));
        academiaSet = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, servicesFactory.getSearchSetsBase().resolve("academia.dat"));
        retroSet = new RankingSearchSet(SearchSetIdentifier.RETRO, servicesFactory.getSearchSetsBase().resolve("retro.dat"));
    }

    public void recalculateAll() {
        updateAcademiaDomains();
        updateRetroDomains();
        updateSmallWebDomains();
    }

    @SneakyThrows
    public void updateRetroDomains() {
        var spr = new StandardPageRank(rankingDomains,rankingSettings.retro.toArray(String[]::new));
        var data = spr.pageRankWithPeripheralNodes(spr.size() / 2, RankingResultBitSetAccumulator::new);

        synchronized (this) {
            retroSet = new RankingSearchSet(SearchSetIdentifier.RETRO, retroSet.source, data);
            retroSet.write();
        }

        var ranks = spr.pageRankWithPeripheralNodes(spr.size() / 2, () -> new RankingResultHashMapAccumulator(100_000));
        synchronized (this) {
            domainRankings = new DomainRankings(ranks);
        }
    }

    @SneakyThrows
    public void updateSmallWebDomains() {
        var rpr = new ReversePageRank(rankingDomains,  rankingSettings.small.toArray(String[]::new));
        rpr.setMaxKnownUrls(750);
        var data = rpr.pageRankWithPeripheralNodes(rpr.size(), RankingResultBitSetAccumulator::new);

        synchronized (this) {
            smallWebSet = new RankingSearchSet(SearchSetIdentifier.SMALLWEB, smallWebSet.source, data);
            smallWebSet.write();
        }
    }

    @SneakyThrows
    public void updateAcademiaDomains() {
        var spr =  new StandardPageRank(rankingDomains,  rankingSettings.academia.toArray(String[]::new));
        var data = spr.pageRankWithPeripheralNodes(spr.size()/2, RankingResultBitSetAccumulator::new);

        synchronized (this) {
            academiaSet = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, academiaSet.source, data);
            academiaSet.write();
        }
    }

    public DomainRankings getDomainRankings() {
        return domainRankings;
    }

    public SearchSet getSearchSetByName(SearchSetIdentifier searchSetIdentifier) {
        if (null == searchSetIdentifier) {
            return anySet;
        }
        return switch (searchSetIdentifier) {
            case NONE -> anySet;
            case RETRO -> retroSet;
            case ACADEMIA -> academiaSet;
            case SMALLWEB -> smallWebSet;
        };
    }
}
