package nu.marginalia.ranking.tool;

import lombok.SneakyThrows;
import nu.marginalia.ranking.accumulator.RankingResultListAccumulator;
import nu.marginalia.ranking.data.RankingDomainFetcher;
import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.ranking.StandardPageRank;
import nu.marginalia.ranking.data.RankingDomainFetcherForSimilarityData;
import nu.marginalia.service.module.DatabaseModule;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class PrintDomainRanksTool {

    private static final Logger logger = LoggerFactory.getLogger(PrintDomainRanksTool.class);

    private volatile static int rankMax;

    static final LinkedBlockingQueue<Integer> uploadQueue = new LinkedBlockingQueue<>(10);
    volatile static boolean running = true;

    @SneakyThrows
    public static void main(String... args) {
        Driver driver = new Driver();
        var conn = new DatabaseModule().provideConnection();

        long start = System.currentTimeMillis();

        logger.info("Ranking");
        var ds = new DatabaseModule().provideConnection();

        RankingDomainFetcher domains;
        if (Boolean.getBoolean("use-link-data")) {
            domains = new RankingDomainFetcher(ds, new DomainBlacklistImpl(ds));
            domains.retainNames();
        }
        else {
            domains = new RankingDomainFetcherForSimilarityData(ds, new DomainBlacklistImpl(ds));
            domains.retainNames();
        }

        var rpr = new StandardPageRank(domains,  args);

        rankMax = rpr.size();

        var rankData = rpr.pageRankWithPeripheralNodes(rankMax, RankingResultListAccumulator::new);

        AtomicInteger cnt = new AtomicInteger();
        rankData.forEach(i -> {

            var data = rpr.getDomainData(i);

            System.out.printf("%d %s %s\n", cnt.getAndIncrement(), data.name, data.state);
            return true;
        });

        long end = System.currentTimeMillis();
        running = false;

        logger.info("Done in {}", (end - start)/1000.0);
    }

}
