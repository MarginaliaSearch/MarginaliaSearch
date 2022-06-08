package nu.marginalia.wmsa.edge.index.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import lombok.SneakyThrows;
import nu.marginalia.util.ranking.BetterReversePageRank;
import nu.marginalia.util.ranking.BetterStandardPageRank;
import nu.marginalia.util.ranking.BuggyStandardPageRank;
import nu.marginalia.util.ranking.RankingDomainFetcher;
import nu.marginalia.wmsa.edge.index.model.RankingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SearchIndexDao {
    private final HikariDataSource dataSource;
    private RankingDomainFetcher rankingDomains;
    private final RankingSettings rankingSettings;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchIndexDao(HikariDataSource dataSource,
                          RankingDomainFetcher rankingDomains,
                          RankingSettings rankingSettings)
    {
        this.dataSource = dataSource;
        this.rankingDomains = rankingDomains;
        this.rankingSettings = rankingSettings;
        logger.info("SearchIndexDao ranking settings = {}", rankingSettings);
    }

    @SneakyThrows
    public TIntHashSet goodUrls() {
        TIntHashSet domains = new TIntHashSet(10_000_000, 0.5f, -1);
        TIntHashSet urls = new TIntHashSet(100_000_000, 0.5f, -1);

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
    public TIntList getRetroDomains() {
        var spr = new BetterStandardPageRank(rankingDomains,rankingSettings.retro.toArray(String[]::new));
        return spr.pageRankWithPeripheralNodes(spr.size()/2);
    }

    @SneakyThrows
    public TIntList getSmallWebDomains() {
        var rpr = new BetterReversePageRank(rankingDomains,  rankingSettings.small.toArray(String[]::new));

        rpr.setMaxKnownUrls(750);

        return rpr.pageRankWithPeripheralNodes(rpr.size());
    }

    @SneakyThrows
    public TIntList getAcademiaDomains() {
        var spr =  new BetterStandardPageRank(rankingDomains,  rankingSettings.academia.toArray(String[]::new));
        return spr.pageRankWithPeripheralNodes(spr.size()/2);
    }

    @SneakyThrows
    public TIntList getStandardDomains() {
        var spr = new BuggyStandardPageRank(rankingDomains,rankingSettings.standard.toArray(String[]::new));
        return spr.pageRankWithPeripheralNodes(spr.size()/2);
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
}
