package nu.marginalia.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import lombok.SneakyThrows;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.id.EdgeIdList;
import nu.marginalia.ranking.ReversePageRank;
import nu.marginalia.ranking.StandardPageRank;
import nu.marginalia.ranking.accumulator.RankingResultHashMapAccumulator;
import nu.marginalia.ranking.accumulator.RankingResultHashSetAccumulator;
import nu.marginalia.ranking.data.RankingDomainFetcher;
import nu.marginalia.ranking.data.RankingDomainFetcherForSimilarityData;
import nu.marginalia.index.svc.searchset.RankingSearchSet;
import nu.marginalia.index.svc.searchset.SearchSetAny;
import nu.marginalia.index.config.RankingSettings;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.index.db.DbUpdateRanks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class IndexSearchSetsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DomainTypes domainTypes;
    private final DbUpdateRanks dbUpdateRanks;
    private final RankingDomainFetcher similarityDomains;
    private final RankingSettings rankingSettings;


    // Below are binary indices that are used to constrain a search
    private volatile RankingSearchSet retroSet;
    private volatile RankingSearchSet smallWebSet;
    private volatile RankingSearchSet academiaSet;
    private volatile RankingSearchSet blogsSet;
    private final SearchSet anySet = new SearchSetAny();

    // The ranking value of the domains used in sorting the domains
    private volatile DomainRankings domainRankings = new DomainRankings();

    @Inject
    public IndexSearchSetsService(DomainTypes domainTypes,
                                  RankingDomainFetcher rankingDomains,
                                  RankingDomainFetcherForSimilarityData similarityDomains,
                                  RankingSettings rankingSettings,
                                  IndexServicesFactory servicesFactory,
                                  DbUpdateRanks dbUpdateRanks) throws IOException {
        this.domainTypes = domainTypes;

        this.dbUpdateRanks = dbUpdateRanks;

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
        blogsSet = new RankingSearchSet(SearchSetIdentifier.BLOGS, servicesFactory.getSearchSetsBase().resolve("blogs.dat"));
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
            case BLOGS -> blogsSet;
        };
    }

    public void recalculateAll() {
        updateAcademiaDomainsSet();
        updateRetroDomainsSet();
        updateSmallWebDomainsSet();
        updateBlogsSet();
        updateDomainRankings();
    }

    private void updateDomainRankings() {
        var entry = rankingSettings.ranking;

        var spr = new StandardPageRank(similarityDomains, entry.domains.toArray(String[]::new));
        var ranks = spr.pageRankWithPeripheralNodes(entry.max, () -> new RankingResultHashMapAccumulator(100_000));

        synchronized (this) {
            domainRankings = new DomainRankings(ranks);
        }

        // The EC_DOMAIN table has a field that reflects the rank, this needs to be set for search result ordering to
        // make sense
        dbUpdateRanks.execute(ranks);
    }

    @SneakyThrows
    public void updateRetroDomainsSet() {
        var entry = rankingSettings.retro;

        var spr = new StandardPageRank(similarityDomains, entry.domains.toArray(String[]::new));
        var data = spr.pageRankWithPeripheralNodes(entry.max, RankingResultHashSetAccumulator::new);

        synchronized (this) {
            retroSet = new RankingSearchSet(SearchSetIdentifier.RETRO, retroSet.source, data);
            retroSet.write();
        }
    }

    @SneakyThrows
    public void updateSmallWebDomainsSet() {
        var entry = rankingSettings.small;

        var rpr = new ReversePageRank(similarityDomains,  entry.domains.toArray(String[]::new));
        rpr.setMaxKnownUrls(750);
        var data = rpr.pageRankWithPeripheralNodes(entry.max, RankingResultHashSetAccumulator::new);

        synchronized (this) {
            smallWebSet = new RankingSearchSet(SearchSetIdentifier.SMALLWEB, smallWebSet.source, data);
            smallWebSet.write();
        }
    }

    @SneakyThrows
    public void updateBlogsSet() {
        EdgeIdList<EdgeDomain> knownDomains = domainTypes.getKnownDomainsByType(DomainTypes.Type.BLOG);

        if (knownDomains.isEmpty()) {
            // FIXME: We don't want to reload the entire list every time, but we do want to do it sometimes. Actor maybe?
            domainTypes.reloadDomainsList(DomainTypes.Type.BLOG);
            knownDomains = domainTypes.getKnownDomainsByType(DomainTypes.Type.BLOG);
        }

        synchronized (this) {
            blogsSet = new RankingSearchSet(SearchSetIdentifier.BLOGS, blogsSet.source, new IntOpenHashSet(knownDomains.values()));
            blogsSet.write();
        }
    }


    @SneakyThrows
    public void updateAcademiaDomainsSet() {
        var entry = rankingSettings.academia;

        var spr =  new StandardPageRank(similarityDomains,  entry.domains.toArray(String[]::new));
        var data = spr.pageRankWithPeripheralNodes(entry.max, RankingResultHashSetAccumulator::new);

        synchronized (this) {
            academiaSet = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, academiaSet.source, data);
            academiaSet.write();
        }
    }
}
