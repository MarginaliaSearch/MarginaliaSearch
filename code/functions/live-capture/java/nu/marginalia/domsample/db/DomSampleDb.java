package nu.marginalia.domsample.db;

import nu.marginalia.WmsaHome;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DomSampleDb implements AutoCloseable {
    private static final String dbFileName = "dom-sample.db";
    private final Connection connection;

    public DomSampleDb() throws SQLException{
        this(WmsaHome.getDataPath().resolve(dbFileName));
    }

    public DomSampleDb(Path dbPath) throws SQLException {
        String dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        connection = DriverManager.getConnection(dbUrl);

        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS samples (url TEXT PRIMARY KEY, domain TEXT, sample BLOB)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS domain_index ON samples (domain)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS schedule (domain TEXT PRIMARY KEY, last_fetch TIMESTAMP DEFAULT NULL)");
        }
    }

    public List<Map.Entry<String, String>> getSamples(String domain) throws SQLException {
        List<Map.Entry<String, String>> samples = new ArrayList<>();

        try (var stmt = connection.prepareStatement("SELECT url, sample FROM samples WHERE domain = ?")) {
            stmt.setString(1, domain);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                samples.add(Map.entry(rs.getString("url"), rs.getString("sample")));
            }
        }
        return samples;
    }

    public void saveSample(String domain, String url, String sample) throws SQLException {
        try (var stmt = connection.prepareStatement("INSERT OR REPLACE INTO samples (domain, url, sample) VALUES (?, ?, ?)")) {
            stmt.setString(1, domain);
            stmt.setString(2, url);
            stmt.setString(3, sample);
            stmt.executeUpdate();
        }
    }

    public void close() throws SQLException {
        connection.close();
    }
}
