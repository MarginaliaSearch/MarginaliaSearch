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
    private final PreparedStatement insertErrorStmt;
    private final PreparedStatement insertEtagStmt;
    private final PreparedStatement insertLastFetchStmt;
    private final Path dbPath;

    private volatile boolean closed = false;

    public FeedDbWriter(Path dbPath) throws SQLException {
        String dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        this.dbPath = dbPath;

        connection = DriverManager.getConnection(dbUrl);

        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS feed (domain TEXT PRIMARY KEY, feed JSON)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS errors (domain TEXT PRIMARY KEY, cnt INT DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS etags (domain TEXT PRIMARY KEY, etag TEXT)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS last_fetch (domain TEXT PRIMARY KEY, fetch_day INT DEFAULT 0)");
        }

        insertFeedStmt = connection.prepareStatement("INSERT OR IGNORE INTO feed (domain, feed) VALUES (?, ?)");
        insertErrorStmt = connection.prepareStatement("INSERT INTO errors (domain, cnt) VALUES (?, ?)");
        insertEtagStmt = connection.prepareStatement("INSERT INTO etags (domain, etag) VALUES (?, ?)");
        insertLastFetchStmt = connection.prepareStatement("INSERT INTO last_fetch (domain, fetch_day) VALUES (?, ?)");
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

    public synchronized void saveFetchDate(String domain, int date) {
        try {
            insertLastFetchStmt.setString(1, domain);
            insertLastFetchStmt.setInt(2, date);
            insertLastFetchStmt.executeUpdate();
        }
        catch (SQLException e) {
            logger.error("Error saving fetch date for " + domain, e);
        }
    }

    public synchronized void saveEtag(String domain, String etag) {
        if (etag == null || etag.isBlank())
            return;

        try {
            insertEtagStmt.setString(1, domain.toLowerCase());
            insertEtagStmt.setString(2, etag);
            insertEtagStmt.executeUpdate();
        }
        catch (SQLException e) {
            logger.error("Error saving etag for " + domain, e);
        }
    }

    public synchronized void setErrorCount(String domain, int count) {
        try {
            insertErrorStmt.setString(1, domain);
            insertErrorStmt.setInt(2, count);
            insertErrorStmt.executeUpdate();
        }
        catch (SQLException ex) {
            logger.error("Error saving error count " + domain, ex);
        }
    }

    private String serialize(FeedItems items) {
        return gson.toJson(items);
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            insertFeedStmt.close();
            insertErrorStmt.close();
            insertEtagStmt.close();
            insertLastFetchStmt.close();
            connection.close();
            closed = true;
        }
    }
}
