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
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.index.model.RankingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SearchIndexDao {
    private final HikariDataSource dataSource;
    private final RankingSettings rankingSettings;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchIndexDao(HikariDataSource dataSource,
                          RankingSettings rankingSettings)
    {
        this.dataSource = dataSource;
        this.rankingSettings = rankingSettings;
        logger.info("SearchIndexDao ranking settings = {}", rankingSettings);
    }

    @SneakyThrows
    public TIntHashSet getSpamDomains() {
        final TIntHashSet result = new TIntHashSet(1_000_000);

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT EC_DOMAIN.ID FROM EC_DOMAIN INNER JOIN EC_TOP_DOMAIN ON EC_DOMAIN.URL_TOP_DOMAIN_ID = EC_TOP_DOMAIN.ID INNER JOIN EC_DOMAIN_BLACKLIST ON EC_DOMAIN_BLACKLIST.URL_DOMAIN = EC_TOP_DOMAIN.URL_PART")) {
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    result.add(rsp.getInt(1));
                }
            }
        }

        return result;
    }

    @SneakyThrows
    public TIntHashSet goodUrls() {
        TIntHashSet domains = new TIntHashSet(10_000_000, 0.5f, -1);
        TIntHashSet urls = new TIntHashSet(100_000_000, 0.5f, -1);

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_ALIAS IS NULL AND STATE>=0")) {
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
        var spr = new BetterStandardPageRank(dataSource,rankingSettings.retro.toArray(String[]::new));
        return spr.pageRankWithPeripheralNodes(spr.size()/2, false);
    }

    @SneakyThrows
    public TIntList getSmallWebDomains() {
        var rpr = new BetterReversePageRank(new DatabaseModule().provideConnection(),  rankingSettings.small.toArray(String[]::new));

        rpr.setMaxKnownUrls(750);

        return rpr.pageRankWithPeripheralNodes(rpr.size(), false);
    }

    @SneakyThrows
    public TIntList getAcademiaDomains() {
        var spr =  new BetterStandardPageRank(new DatabaseModule().provideConnection(),  rankingSettings.academia.toArray(String[]::new));
        return spr.pageRankWithPeripheralNodes(spr.size()/2, false);
    }

    @SneakyThrows
    public TIntList getStandardDomains() {
        var spr = new BuggyStandardPageRank(dataSource,rankingSettings.standard.toArray(String[]::new));
        return spr.pageRankWithPeripheralNodes(spr.size()/2, false);
    }

    @SneakyThrows
    public TIntList getSpecialDomains() {
        TIntArrayList results = new TIntArrayList();
        try (var connection = dataSource.getConnection();
             var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE STATE=2")
        ) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(rs.getInt(1));
            }
        }
        return results;
    }
}
