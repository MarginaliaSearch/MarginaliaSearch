package nu.marginalia.encyclopedia.store;

import nu.marginalia.encyclopedia.cleaner.model.ArticleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ArticleStoreWriter implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Connection connection;
    private final LinkedBlockingQueue<ArticleData> queue = new LinkedBlockingQueue<>(1000);

    Thread insertThread;
    volatile boolean running;

    public ArticleStoreWriter(ArticleDbProvider dbProvider) throws SQLException {
        connection = dbProvider.getConnection();

        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA synchronous = OFF");
            stmt.execute("PRAGMA journal_mode = MEMORY");
        }

        running = true;
        insertThread = new Thread(this::insertLoop);
        insertThread.start();
    }

    private void insertLoop() {
        List<ArticleData> toAdd = new ArrayList<>();
        while (running || !queue.isEmpty()) {
            try {
                while (0 != queue.drainTo(toAdd, 100)) {
                    insertItems(toAdd);
                    toAdd.clear();
                }
                if (queue.isEmpty()) {
                    // Yield for a moment to avoid busy looping
                    TimeUnit.NANOSECONDS.sleep(100);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void insertItems(List<ArticleData> toAdd) throws SQLException {
        try (var stmt = connection.prepareStatement("""
        INSERT OR IGNORE INTO articles (url, title, html, summary, urls, disambigs)
        VALUES (?, ?, ?, ?, ?, ?)
        """))
        {
            connection.setAutoCommit(false); // Disable auto-commit mode
            for (var article : toAdd) {
                stmt.setString(1, article.url());
                stmt.setString(2, article.title());
                stmt.setBytes(3, article.parts());
                stmt.setString(4, article.summary());
                stmt.setBytes(5, article.links());
                stmt.setBytes(6, article.disambigs());

                stmt.addBatch();
            }
            stmt.executeBatch();
            connection.commit(); // Commit the transaction
        } catch (SQLException e) {
            connection.rollback(); // Rollback the transaction in case of error
            logger.warn("SQL error", e);
        } finally {
            connection.setAutoCommit(true); // Re-enable auto-commit mode
        }
    }

    public void add(ArticleData article)  {
        try {
            queue.put(article);
        }
        catch (InterruptedException e) {
            logger.warn("Interrupted", e);
            throw new RuntimeException(e);
        }
    }

    public void close() {
        running = false;
        try {
            insertThread.join();
            connection.close();
        } catch (InterruptedException|SQLException e) {
            logger.warn("Error", e);
        }
    }

}
