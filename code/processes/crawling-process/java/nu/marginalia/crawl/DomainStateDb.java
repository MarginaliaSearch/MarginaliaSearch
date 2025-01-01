package nu.marginalia.crawl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Supplemental sqlite database for storing the summary of a crawl.
 *  One database exists per crawl data set.
 * */
public class DomainStateDb implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DomainStateDb.class);

    private final Connection connection;

    public record SummaryRecord(
            String domainName,
            Instant lastUpdated,
            String state,
            @Nullable String stateDesc,
            @Nullable String feedUrl
    )
    {
        public static SummaryRecord forSuccess(String domainName) {
            return new SummaryRecord(domainName, Instant.now(), "OK", null, null);
        }

        public static SummaryRecord forSuccess(String domainName, String feedUrl) {
            return new SummaryRecord(domainName, Instant.now(), "OK", null, feedUrl);
        }

        public static SummaryRecord forError(String domainName, String state, String stateDesc) {
            return new SummaryRecord(domainName, Instant.now(), state, stateDesc, null);
        }

        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof SummaryRecord(String name, Instant updated, String state1, String desc, String url))) {
                return false;
            }
            return domainName.equals(name) &&
                    lastUpdated.toEpochMilli() == updated.toEpochMilli() &&
                    state.equals(state1) &&
                    (stateDesc == null ? desc == null : stateDesc.equals(desc)) &&
                    (feedUrl == null ? url == null : feedUrl.equals(url));
        }

        public int hashCode() {
            return domainName.hashCode() + Long.hashCode(lastUpdated.toEpochMilli());
        }

    }

    public DomainStateDb(Path filename) throws SQLException {
        String sqliteDbString = "jdbc:sqlite:" + filename.toString();
        connection = DriverManager.getConnection(sqliteDbString);

        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS summary (
                        domain TEXT PRIMARY KEY,
                        lastUpdatedEpochMs LONG NOT NULL,
                        state TEXT NOT NULL,
                        stateDesc TEXT,
                        feedUrl TEXT
                    )
                    """);

            stmt.execute("PRAGMA journal_mode=WAL");
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }


    public void save(SummaryRecord record) {
        try (var stmt = connection.prepareStatement("""
                INSERT OR REPLACE INTO summary (domain, lastUpdatedEpochMs, state, stateDesc, feedUrl)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            stmt.setString(1, record.domainName());
            stmt.setLong(2, record.lastUpdated().toEpochMilli());
            stmt.setString(3, record.state());
            stmt.setString(4, record.stateDesc());
            stmt.setString(5, record.feedUrl());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert summary record", e);
        }
    }

    public Optional<SummaryRecord> get(String domainName) {
        try (var stmt = connection.prepareStatement("""
                SELECT domain, lastUpdatedEpochMs, state, stateDesc, feedUrl
                FROM summary
                WHERE domain = ?
                """)) {
            stmt.setString(1, domainName);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new SummaryRecord(
                        rs.getString("domain"),
                        Instant.ofEpochMilli(rs.getLong("lastUpdatedEpochMs")),
                        rs.getString("state"),
                        rs.getString("stateDesc"),
                        rs.getString("feedUrl")
                ));
            }
        } catch (SQLException e) {
            logger.error("Failed to get summary record", e);
        }

        return Optional.empty();
    }
}
