package nu.marginalia.adjacencies;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class AdjacenciesLoader {

    private static final Logger logger = LoggerFactory.getLogger(AdjacenciesLoader.class);

    private final HikariDataSource dataSource;
    private final LinkedBlockingDeque<WebsiteAdjacenciesCalculator.DomainSimilarities> similaritiesLinkedBlockingDeque = new LinkedBlockingDeque<>(100);
    private final Thread loaderThread;

    volatile boolean running = true;

    public AdjacenciesLoader(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        loaderThread = Thread.ofPlatform().name("Adjacencies Loader Thread").start(this::insertThreadRun);
    }

    public void load(WebsiteAdjacenciesCalculator.DomainSimilarities similarities) {
        try {
            similaritiesLinkedBlockingDeque.putLast(similarities);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
                int itemCount = 0;

                while (running || !similaritiesLinkedBlockingDeque.isEmpty()) {
                    for (var item = similaritiesLinkedBlockingDeque.pollFirst(1, TimeUnit.SECONDS);
                         item != null;
                         item = similaritiesLinkedBlockingDeque.pollFirst())
                    {
                        for (var similarity : item.similarities()) {
                            stmt.setInt(1, item.domainId());
                            stmt.setInt(2, similarity.domainId());
                            stmt.setDouble(3, similarity.value());
                            stmt.addBatch();
                            itemCount++;
                        }

                        if (itemCount++ > 1000) {
                            stmt.executeBatch();
                            itemCount = 0;
                        }
                    }
                }

                // Flush remaining items
                if (itemCount > 0) {
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
