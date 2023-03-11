package nu.marginalia.ranking.tool;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.ranking.StandardPageRank;
import nu.marginalia.ranking.accumulator.RankingResultListAccumulator;
import nu.marginalia.ranking.data.RankingDomainFetcherForSimilarityData;

import nu.marginalia.model.dbcommon.DomainBlacklistImpl;
import nu.marginalia.service.module.DatabaseModule;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;

public class UpdateDomainRanksTool {

    private static final Logger logger = LoggerFactory.getLogger(UpdateDomainRanksTool.class);

    private volatile static int rankMax;

    static final LinkedBlockingQueue<Integer> uploadQueue = new LinkedBlockingQueue<>(10);
    volatile static boolean running = true;

    @SneakyThrows
    public static void main(String... args) {
        Driver driver = new Driver();
        var conn = new DatabaseModule().provideConnection();

        long start = System.currentTimeMillis();
        var uploader = new Thread(() -> uploadThread(conn), "Uploader");

        logger.info("Ranking");
        var domains = new RankingDomainFetcherForSimilarityData(conn, new DomainBlacklistImpl(conn));
        var rpr = new StandardPageRank(domains,  "memex.marginalia.nu", "bikobatanari.art", "sadgrl.online", "wiki.xxiivv.com");

        rankMax = rpr.size();
        uploader.start();

        var rankData = rpr.pageRankWithPeripheralNodes(rankMax, RankingResultListAccumulator::new);

        rankData.forEach(i -> {
            try {
                uploadQueue.put(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        });

        long end = System.currentTimeMillis();
        running = false;
        uploader.join();

        logger.info("Done in {}", (end - start)/1000.0);
    }

    public static void uploadThread(HikariDataSource dataSource) {
        int i = 0;

        try (var conn = dataSource.getConnection()) {
            logger.info("Resetting rank");
            try (var stmt = conn.prepareStatement("UPDATE EC_DOMAIN SET RANK=1")) {
                stmt.executeUpdate();
            }

            logger.info("Updating ranks");
            try (var stmt = conn.prepareStatement("UPDATE EC_DOMAIN SET RANK=? WHERE ID=?")) {
                while (running || (!running && !uploadQueue.isEmpty())) {
                    var job = uploadQueue.take();
                    stmt.setDouble(1, i++ / (double) rankMax);
                    stmt.setInt(2, job);
                    stmt.executeUpdate();
                }
            }

            logger.info("Recalculating quality");

        } catch (SQLException | InterruptedException throwables) {
            throwables.printStackTrace();
        }
    }
}
