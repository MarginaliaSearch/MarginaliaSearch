package nu.marginalia.pq;

import nu.marginalia.model.EdgeUrl;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PersistentQueryResultDb implements AutoCloseable {
    private final Connection connection;

    public PersistentQueryResultDb(Path basePath, PersistentQuerySpec spec) throws SQLException {
        this(basePath.resolve(spec.publicId() + ".db"));
    }

    public PersistentQueryResultDb(Path sqliteFile) throws SQLException {
        String connStr = "jdbc:sqlite:" + sqliteFile;

        connection = DriverManager.getConnection(connStr);

        try (var stmt =  connection.createStatement()) {

            stmt.execute("PRAGMA journal_mode=WAL");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS RESULTS (
                        ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        URL TEXT UNIQUE NOT NULL,
                        TITLE TEXT NOT NULL,
                        ADDED_TS INTEGER NOT NULL
                    ) STRICT
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS SUMMARIES (
                        RUN_TS INTEGER PRIMARY KEY,
                        COUNT INTEGER UNIQUE NOT NULL
                    ) STRICT
                    """);
        }
    }

    public boolean addResult(EdgeUrl url,
                             String title,
                             Instant discoveryTimestamp
                             ) throws SQLException {

        try (var stmt = connection.prepareStatement("""
                INSERT OR IGNORE INTO RESULTS(URL, TITLE, ADDED_TS) 
                VALUES (?,?,?)
                """)) {

            stmt.setString(1, url.toString());
            stmt.setString(2, title.toLowerCase());
            stmt.setLong(3, discoveryTimestamp.getEpochSecond());

            return 1 == stmt.executeUpdate();
        }
    }

    public List<PersistentQueryResult> getResultsSince(Instant cutoff,
                                                       int afterId,
                                                       int count) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT 
                    ID,
                    URL,
                    TITLE,
                    ADDED_TS 
                FROM RESULTS 
                WHERE ADDED_TS > ? 
                AND ID > ? 
                ORDER BY ADDED_TS, ID
                LIMIT ?
                """)) {

            query.setLong(1, cutoff.getEpochSecond());
            query.setInt(2, afterId);
            query.setInt(3, count);

            var rs =  query.executeQuery();

            List<PersistentQueryResult> results = new ArrayList<>();

            while (rs.next()) {
                results.add(new PersistentQueryResult(
                        rs.getInt("ID"),
                        rs.getString("URL"),
                        rs.getString("TITLE"),
                        Instant.ofEpochSecond(rs.getLong("ADDED_TS"))
                ));
            }

            return results;
        }
    }

    /** Add a summary record */
    public void addSummary(Instant queryTime, int discoveries) throws SQLException {
        try (var stmt = connection.prepareStatement("""
                INSERT OR IGNORE INTO SUMMARIES(RUN_TS, COUNT) 
                VALUES (?,?)
                """)) {

            stmt.setLong(1, queryTime.getEpochSecond());
            stmt.setInt(2, discoveries);

            stmt.executeUpdate();
        }
    }

    public void close()  throws SQLException {
        connection.close();
    }
}
