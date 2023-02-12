package nu.marginalia.wmsa.edge.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.ranking.ReversePageRank;
import nu.marginalia.wmsa.edge.index.ranking.StandardPageRank;
import nu.marginalia.wmsa.edge.index.ranking.accumulator.RankingResultHashMapAccumulator;
import nu.marginalia.wmsa.edge.index.ranking.data.RankingDomainFetcher;
import nu.marginalia.wmsa.edge.index.ranking.accumulator.RankingResultBitSetAccumulator;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.model.RankingSettings;
import nu.marginalia.wmsa.edge.index.postings.DomainRankings;
import nu.marginalia.wmsa.edge.index.ranking.data.RankingDomainFetcherForSimilarityData;
import nu.marginalia.wmsa.edge.index.svc.searchset.RankingSearchSet;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSet;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetAny;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class EdgeIndexSearchSetsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final RankingDomainFetcher rankingDomains;
    private final RankingDomainFetcher similarityDomains;
    private final RankingSettings rankingSettings;


    // Below are binary indices that are used to constrain a search
    private volatile RankingSearchSet retroSet;
    private volatile RankingSearchSet smallWebSet;
    private volatile RankingSearchSet academiaSet;
    private final SearchSet anySet = new SearchSetAny();

    // The ranking value of the domains used in sorting the domains
    private volatile DomainRankings domainRankings = new DomainRankings();

    @Inject
    public EdgeIndexSearchSetsService(RankingDomainFetcher rankingDomains,
                                      RankingDomainFetcherForSimilarityData similarityDomains,
                                      RankingSettings rankingSettings,
                                      IndexServicesFactory servicesFactory) throws IOException {

        this.rankingDomains = rankingDomains;

        if (similarityDomains.hasData()) {
            this.similarityDomains = similarityDomains;
        }
        else {
            // on test environments the cosine similarity graph may not be present
            logger.info("Domain similarity is not present, falling back on link graph");
            this.similarityDomains = rankingDomains;
        }

        this.rankingSettings = rankingSettings;

        smallWebSet = new RankingSearchSet(SearchSetIdentifier.SMALLWEB, servicesFactory.getSearchSetsBase().resolve("small-web.dat"));
        academiaSet = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, servicesFactory.getSearchSetsBase().resolve("academia.dat"));
        retroSet = new RankingSearchSet(SearchSetIdentifier.RETRO, servicesFactory.getSearchSetsBase().resolve("retro.dat"));
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

    public void recalculateAll() {
        updateAcademiaDomainsSet();
        updateRetroDomainsSet();
        updateSmallWebDomainsSet();
        updateDomainRankings();
    }

    private void updateDomainRankings() {
        var spr = new StandardPageRank(similarityDomains, rankingSettings.retro.toArray(String[]::new));

        var ranks = spr.pageRankWithPeripheralNodes(Math.min(100_000, spr.size() / 2), () -> new RankingResultHashMapAccumulator(100_000));
        synchronized (this) {
            domainRankings = new DomainRankings(ranks);
        }
    }

    @SneakyThrows
    public void updateRetroDomainsSet() {
        var spr = new StandardPageRank(similarityDomains, rankingSettings.retro.toArray(String[]::new));
        var data = spr.pageRankWithPeripheralNodes(Math.min(50_000, spr.size()), RankingResultBitSetAccumulator::new);

        synchronized (this) {
            retroSet = new RankingSearchSet(SearchSetIdentifier.RETRO, retroSet.source, data);
            retroSet.write();
        }
    }

    @SneakyThrows
    public void updateSmallWebDomainsSet() {
        var rpr = new ReversePageRank(similarityDomains,  rankingSettings.small.toArray(String[]::new));
        rpr.setMaxKnownUrls(750);
        var data = rpr.pageRankWithPeripheralNodes(Math.min(10_000, rpr.size()), RankingResultBitSetAccumulator::new);

        synchronized (this) {
            smallWebSet = new RankingSearchSet(SearchSetIdentifier.SMALLWEB, smallWebSet.source, data);
            smallWebSet.write();
        }
    }

    @SneakyThrows
    public void updateAcademiaDomainsSet() {
        var spr =  new StandardPageRank(similarityDomains,  rankingSettings.academia.toArray(String[]::new));
        var data = spr.pageRankWithPeripheralNodes(Math.min(15_000, spr.size()/2), RankingResultBitSetAccumulator::new);

        synchronized (this) {
            academiaSet = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, academiaSet.source, data);
            academiaSet.write();
        }
    }
}
