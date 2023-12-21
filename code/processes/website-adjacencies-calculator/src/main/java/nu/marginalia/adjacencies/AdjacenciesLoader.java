package nu.marginalia.adjacencies;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class AdjacenciesLoader {

    private static final Logger logger = LoggerFactory.getLogger(AdjacenciesLoader.class);

    final HikariDataSource dataSource;
    final LinkedBlockingDeque<WebsiteAdjacenciesCalculator.DomainSimilarities> similaritiesLinkedBlockingDeque = new LinkedBlockingDeque<>(100);
    final Thread loaderThread;

    volatile boolean running = true;

    public AdjacenciesLoader(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        loaderThread = new Thread(this::insertThreadRun, "Adjacencies Loader Thread");
        loaderThread.start();
    }

    @SneakyThrows
    public void load(WebsiteAdjacenciesCalculator.DomainSimilarities similarities) {
        similaritiesLinkedBlockingDeque.putLast(similarities);
    }

    private void insertThreadRun() {
        try (var conn = dataSource.getConnection();
             var s = conn.createStatement()
        ) {
            s.execute("""
                    DROP TABLE IF EXISTS EC_DOMAIN_NEIGHBORS_TMP
                    """);
            s.execute("""
                    CREATE TABLE EC_DOMAIN_NEIGHBORS_TMP LIKE EC_DOMAIN_NEIGHBORS_2
                    """);

            try (var stmt = conn.prepareStatement(
                    """
                    INSERT INTO EC_DOMAIN_NEIGHBORS_TMP (DOMAIN_ID, NEIGHBOR_ID, RELATEDNESS) VALUES (?, ?, ?)
                    """))
            {
                while (running || !similaritiesLinkedBlockingDeque.isEmpty()) {
                    var item = similaritiesLinkedBlockingDeque.pollFirst(1, TimeUnit.SECONDS);
                    if (item == null) continue;

                    for (; item != null; item = similaritiesLinkedBlockingDeque.pollFirst()) {
                        for (var similarity : item.similarities()) {
                            stmt.setInt(1, item.domainId());
                            stmt.setInt(2, similarity.domainId());
                            stmt.setDouble(3, similarity.value());
                            stmt.addBatch();
                        }
                    }
                    stmt.executeBatch();
                }
            }

            logger.info("Loader thread wrapping up");

            s.execute("""
                    DROP TABLE IF EXISTS EC_DOMAIN_NEIGHBORS_2
                    """);
            s.execute("""
                    RENAME TABLE EC_DOMAIN_NEIGHBORS_TMP TO EC_DOMAIN_NEIGHBORS_2
                    """);

        } catch (SQLException | InterruptedException e) {
            logger.error("Failed to insert into database", e);
            throw new RuntimeException(e);
        }

        logger.info("Loader thread finished");
    }

    public void stop() throws InterruptedException {
        running = false;
        loaderThread.join();
    }
}
