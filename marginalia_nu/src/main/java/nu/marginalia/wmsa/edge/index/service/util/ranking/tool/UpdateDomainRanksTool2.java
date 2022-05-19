package nu.marginalia.wmsa.edge.index.service.util.ranking.tool;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.index.service.util.ranking.BetterReversePageRank;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class UpdateDomainRanksTool2 {

    private static final Logger logger = LoggerFactory.getLogger(UpdateDomainRanksTool2.class);

    public Set<String> originDomains = new HashSet<>();
    public Set<Integer> originDomainIds = new HashSet<>();
    public long domainIdMax = -1;
    public int domainCount;
    private volatile static int rankMax;

    public int maxId() {
        return (int) domainIdMax;
    }
    public int domainCount() {
        return domainCount;
    }

    static LinkedBlockingQueue<Integer> uploadQueue = new LinkedBlockingQueue<>(10);
    volatile static boolean running = true;

    @SneakyThrows
    public static void main(String... args) throws IOException {
        Driver driver = new Driver();
        var conn = new DatabaseModule().provideConnection();

        long start = System.currentTimeMillis();
        var uploader = new Thread(() -> uploadThread(conn), "Uploader");

        logger.info("Ranking");
        // "memex.marginalia.nu", "wiki.xxiivv.com", "bikobatanari.art", "sadgrl.online", "lileks.com",
        // "www.rep.routledge.com", "www.personal.kent.edu", "xroads.virginia.edu", "classics.mit.edu", "faculty.washington.edu", "monadnock.net"
         var rpr = new BetterReversePageRank(new DatabaseModule().provideConnection(),  "memex.marginalia.nu", "bikobatanari.art", "sadgrl.online", "wiki.xxiivv.com", "%neocities.org");
//        var rpr = new BetterStandardPageRank(new DatabaseModule().provideConnection(),  "%edu");
//        var spr = new BetterStandardPageRank(new DatabaseModule().provideConnection(), "memex.marginalia.nu");

        var rankVector = rpr.pageRankVector();
        var norm = rankVector.norm();
        rankMax = rpr.size();
        uploader.start();


        rankMax = rpr.size();


        rpr.pageRankWithPeripheralNodes(rankMax, false).forEach(i -> {
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
            try (var stmt = conn.prepareStatement("UPDATE EC_DOMAIN SET QUALITY=-5*RANK+IF(RANK=1,RANK*GREATEST(QUALITY_RAW,QUALITY_ORIGINAL)/2, 0)")) {
                stmt.executeUpdate();
            }

        } catch (SQLException | InterruptedException throwables) {
            throwables.printStackTrace();
        }
    }
}
