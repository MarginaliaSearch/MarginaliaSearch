package nu.marginalia.ranking.tool;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.model.dbcommon.EdgeDomainBlacklistImpl;
import nu.marginalia.ranking.StandardPageRank;
import nu.marginalia.ranking.accumulator.RankingResultListAccumulator;
import nu.marginalia.ranking.data.RankingDomainFetcherForSimilarityData;
import nu.marginalia.service.module.DatabaseModule;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;

public class CreateBrowseDomainRanksTool {

    private static final Logger logger = LoggerFactory.getLogger(CreateBrowseDomainRanksTool.class);


    static final LinkedBlockingQueue<Integer> uploadQueue = new LinkedBlockingQueue<>(10);
    volatile static boolean running = true;

    @SneakyThrows
    public static void main(String... args) {
        Driver driver = new Driver();
        var conn = new DatabaseModule().provideConnection();

        long start = System.currentTimeMillis();
        var uploader = new Thread(() -> uploadThread(conn), "Uploader");

        logger.info("Ranking");
        var ds = new DatabaseModule().provideConnection();
        var domains = new RankingDomainFetcherForSimilarityData(ds, new EdgeDomainBlacklistImpl(ds));
        var rpr = new StandardPageRank(domains,  args);

        uploader.start();

        var rankData = rpr.pageRankWithPeripheralNodes(1000, RankingResultListAccumulator::new);

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
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("INSERT IGNORE INTO EC_RANDOM_DOMAINS(DOMAIN_SET, DOMAIN_ID) VALUES (3, ?)")) {
                while (running || (!running && !uploadQueue.isEmpty())) {
                    var job = uploadQueue.take();
                    stmt.setInt(1, job);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException | InterruptedException throwables) {
            throwables.printStackTrace();
        }
    }
}
