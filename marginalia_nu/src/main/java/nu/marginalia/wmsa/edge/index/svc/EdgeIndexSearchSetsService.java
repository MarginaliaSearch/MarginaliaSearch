package nu.marginalia.wmsa.edge.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import lombok.SneakyThrows;
import nu.marginalia.util.ranking.BetterReversePageRank;
import nu.marginalia.util.ranking.BetterStandardPageRank;
import nu.marginalia.util.ranking.RankingDomainFetcher;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.model.RankingSettings;
import nu.marginalia.wmsa.edge.index.svc.searchset.RankingSearchSet;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSet;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetAny;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetIdentifier;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class EdgeIndexSearchSetsService {
    private final HikariDataSource dataSource;
    private RankingDomainFetcher rankingDomains;
    private final RankingSettings rankingSettings;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SearchSet anySet = new SearchSetAny();
    private volatile RankingSearchSet retroSet;
    private volatile RankingSearchSet smallWebSet;
    private volatile RankingSearchSet academiaSet;

    @Inject
    public EdgeIndexSearchSetsService(HikariDataSource dataSource,
                                      RankingDomainFetcher rankingDomains,
                                      RankingSettings rankingSettings,
                                      IndexServicesFactory servicesFactory) throws IOException {
        this.dataSource = dataSource;
        this.rankingDomains = rankingDomains;
        this.rankingSettings = rankingSettings;

        smallWebSet = new RankingSearchSet(SearchSetIdentifier.SMALLWEB, servicesFactory.getSearchSetsBase().resolve("small-web.dat"));
        academiaSet = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, servicesFactory.getSearchSetsBase().resolve("academia.dat"));
        retroSet = new RankingSearchSet(SearchSetIdentifier.RETRO, servicesFactory.getSearchSetsBase().resolve("retro.dat"));

        logger.info("SearchIndexDao ranking settings = {}", rankingSettings);
    }

    public void recalculateAll() {
        updateAcademiaDomains();
        updateRetroDomains();
        updateSmallWebDomains();
    }

    @SneakyThrows
    public RoaringBitmap goodUrls() {
        RoaringBitmap domains = new RoaringBitmap();
        RoaringBitmap urls = new RoaringBitmap();

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_ALIAS IS NULL AND IS_ALIVE")) {
                stmt.setFetchSize(10_000);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    domains.add(rsp.getInt(1));
                }
            }

            // For some reason, doing this "INNER JOIN" in Java is significantly faster than doing it in SQL
            try (var stmt = connection.prepareStatement("SELECT ID,DOMAIN_ID FROM EC_URL WHERE VISITED AND EC_URL.STATE='OK'")) {
                stmt.setFetchSize(10_000);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    if (domains.contains(rsp.getInt(2))) {
                        urls.add(rsp.getInt(1));
                    }
                }
            }

        }

        return urls;
    }

    @SneakyThrows
    public void updateRetroDomains() {
        var spr = new BetterStandardPageRank(rankingDomains,rankingSettings.retro.toArray(String[]::new));
        var data = spr.pageRankWithPeripheralNodes(spr.size() / 2);

        synchronized (this) {
            retroSet = new RankingSearchSet(SearchSetIdentifier.RETRO, retroSet.source, data);
            retroSet.write();
        }
    }

    @SneakyThrows
    public void updateSmallWebDomains() {
        var rpr = new BetterReversePageRank(rankingDomains,  rankingSettings.small.toArray(String[]::new));
        rpr.setMaxKnownUrls(750);
        var data = rpr.pageRankWithPeripheralNodes(rpr.size());

        synchronized (this) {
            smallWebSet = new RankingSearchSet(SearchSetIdentifier.SMALLWEB, smallWebSet.source, data);
            smallWebSet.write();
        }
    }

    @SneakyThrows
    public void updateAcademiaDomains() {
        var spr =  new BetterStandardPageRank(rankingDomains,  rankingSettings.academia.toArray(String[]::new));
        var data = spr.pageRankWithPeripheralNodes(spr.size()/2);

        synchronized (this) {
            academiaSet = new RankingSearchSet(SearchSetIdentifier.ACADEMIA, academiaSet.source, data);
            academiaSet.write();
        }
    }

    @SneakyThrows
    public TIntList getStandardDomains() {
        TIntArrayList results = new TIntArrayList();

        try (var connection = dataSource.getConnection();
             var stmt = connection.prepareStatement(
            """
            SELECT ID FROM EC_DOMAIN 
            WHERE INDEXED>0 
            AND STATE='ACTIVE' 
            AND DOMAIN_ALIAS IS NULL 
            ORDER BY ID ASC
            """);
        ) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(rs.getInt(1));
            }
        }
        return results;

    }

    @SneakyThrows
    public TIntList getSpecialDomains() {
        TIntArrayList results = new TIntArrayList();
        try (var connection = dataSource.getConnection();
             var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE STATE='SPECIAL'")
        ) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(rs.getInt(1));
            }
        }
        return results;
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
