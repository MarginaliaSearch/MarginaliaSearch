package nu.marginalia.rss.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.rss.model.FeedItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class FeedDbWriter implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FeedDbWriter.class);

    private static final Gson gson = new GsonBuilder().create();

    private final Connection connection;
    private final PreparedStatement insertFeedStmt;
    private final Path dbPath;

    private volatile boolean closed = false;

    public FeedDbWriter(Path dbPath) throws SQLException {
        String dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        this.dbPath = dbPath;

        connection = DriverManager.getConnection(dbUrl);

        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS feed (domain TEXT PRIMARY KEY, feed JSON)");
        }

        insertFeedStmt = connection.prepareStatement("INSERT INTO feed (domain, feed) VALUES (?, ?)");
    }

    public Path getDbPath() {
        return dbPath;
    }


    public synchronized void saveFeed(FeedItems items) {
        try {
            insertFeedStmt.setString(1, items.domain().toLowerCase());
            insertFeedStmt.setString(2, serialize(items));
            insertFeedStmt.executeUpdate();
        }
        catch (SQLException e) {
            logger.error("Error saving feed for " + items.domain(), e);
        }
    }

    private String serialize(FeedItems items) {
        return gson.toJson(items);
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            insertFeedStmt.close();
            connection.close();
            closed = true;
        }
    }
}
