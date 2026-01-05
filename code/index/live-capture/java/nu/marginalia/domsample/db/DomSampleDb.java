package nu.marginalia.domsample.db;

import nu.marginalia.WmsaHome;
import nu.marginalia.model.EdgeUrl;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class DomSampleDb implements AutoCloseable {
    private static final String dbFileName = "dom-sample.db";
    private final Connection connection;
    private static final Logger logger = LoggerFactory.getLogger(DomSampleDb.class);

    public DomSampleDb() throws SQLException{
        this(WmsaHome.getDataPath().resolve(dbFileName));
    }

    public DomSampleDb(Path dbPath) throws SQLException {
        String dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        connection = DriverManager.getConnection(dbUrl);

        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS samples (url TEXT PRIMARY KEY, domain TEXT, sample BLOB, requests BLOB, accepted_popover BOOLEAN DEFAULT FALSE)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS domain_index ON samples (domain)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS schedule (domain TEXT PRIMARY KEY, last_fetch TIMESTAMP DEFAULT NULL)");
            stmt.execute("PRAGMA journal_mode=WAL");
        }

    }

    public void syncDomains(Set<String> domains) {
        Set<String> currentDomains = new HashSet<>();
        try (var stmt = connection.prepareStatement("SELECT domain FROM schedule")) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                currentDomains.add(rs.getString("domain"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync domains", e);
        }

        Set<String> toRemove = new HashSet<>(currentDomains);
        Set<String> toAdd = new HashSet<>(domains);

        toRemove.removeAll(domains);
        toAdd.removeAll(currentDomains);

        try (var removeStmt = connection.prepareStatement("DELETE FROM schedule WHERE domain = ?");
                var addStmt = connection.prepareStatement("INSERT OR IGNORE INTO schedule (domain) VALUES (?)")
        ) {
            for (String domain : toRemove) {
                removeStmt.setString(1, domain);
                removeStmt.executeUpdate();
            }

            for (String domain : toAdd) {
                addStmt.setString(1, domain);
                addStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove domains", e);
        }
    }

    public List<String> getScheduledDomains() {
        List<String> domains = new ArrayList<>();
        try (var stmt = connection.prepareStatement("SELECT domain FROM schedule ORDER BY last_fetch IS NULL DESC, last_fetch ASC")) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                domains.add(rs.getString("domain"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get scheduled domains", e);
        }
        return domains;
    }

    public void flagDomainAsFetched(String domain) {
        try (var stmt = connection.prepareStatement("INSERT OR REPLACE INTO schedule (domain, last_fetch) VALUES (?, CURRENT_TIMESTAMP)")) {
            stmt.setString(1, domain);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to flag domain as fetched", e);
        }
    }


    public record Sample(String url, String domain, String sample, String requests, boolean acceptedPopover) {

        public List<SampleRequest> parseRequests() {
            List<SampleRequest> requests = new ArrayList<>();

            // Request format is METHOD\tTIMESTAMP\tURI\n

            for (var line : StringUtils.split(this.requests, '\n')) {
                String[] parts = StringUtils.split(line, "\t", 3);
                if (parts.length != 3) continue;

                try {
                    String method =  parts[0];
                    long ts = Long.parseLong(parts[1]);
                    String linkUrl = parts[2];

                    URI uri = parseURI(linkUrl);

                    requests.add(new SampleRequest(method, ts, uri));
                }
                catch (Exception e) {
                    logger.warn("Failed to parse requests", e);
                }
            }

            return requests;
        }


        private static URI parseURI(String uri) throws URISyntaxException {
            try {
                return new URI(uri);
            }
            catch (URISyntaxException ex) {
                return new EdgeUrl(uri).asURI();
            }
        }
    }

    public record SampleRequest(String method, long timestamp, URI uri) {}

    /**
     * @param consumer - consume the sample, return true to continue consumption
     * @throws SQLException
     */
    public void forEachSample(Predicate<Sample> consumer) throws SQLException {
        try (var stmt = connection.prepareStatement("""
                SELECT url, domain, sample, requests, accepted_popover
                FROM samples
                """))
        {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                var sample = new Sample(
                        rs.getString("url"),
                        rs.getString("domain"),
                        rs.getString("sample"),
                        rs.getString("requests"),
                        rs.getBoolean("accepted_popover")
                );

                if (!consumer.test(sample)) break;
            }
        }
    }

    public List<Sample> getSamples(String domain) throws SQLException {
        List<Sample> samples = new ArrayList<>();

        try (var stmt = connection.prepareStatement("""
                SELECT url, sample, requests, accepted_popover
                FROM samples
                WHERE domain = ?
                """))
        {
            stmt.setString(1, domain);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                samples.add(
                        new Sample(
                                rs.getString("url"),
                                domain,
                                rs.getString("sample"),
                                rs.getString("requests"),
                                rs.getBoolean("accepted_popover")
                        )
                );
            }
        }
        return samples;
    }


    public boolean hasSample(String domain) throws SQLException {

        try (var stmt = connection.prepareStatement("""
                SELECT 1
                FROM samples
                WHERE domain = ?
                """))
        {
            stmt.setString(1, domain);
            var rs = stmt.executeQuery();
            return rs.next();
        }
    }

    public void saveSample(String domain, String url, String rawContent) throws SQLException {
        var doc = Jsoup.parse(rawContent);

        var networkRequests = doc.getElementById("marginalia-network-requests");

        boolean acceptedPopover = false;

        StringBuilder requestTsv = new StringBuilder();
        if (networkRequests != null) {

            acceptedPopover = !networkRequests.getElementsByClass("marginalia-agreed-cookies").isEmpty();

            for (var request : networkRequests.getElementsByClass("network-request")) {
                String method = request.attr("data-method");
                String urlAttr = request.attr("data-url");
                String timestamp = request.attr("data-timestamp");

                requestTsv
                        .append(method)
                        .append('\t')
                        .append(timestamp)
                        .append('\t')
                        .append(urlAttr.replace('\n', ' '))
                        .append("\n");
            }

            networkRequests.remove();
        }

        doc.body().removeAttr("id");

        String sample = doc.html();

        saveSampleRaw(domain, url, sample, requestTsv.toString().trim(), acceptedPopover);

    }

    public void saveSampleRaw(String domain, String url, String sample, String requests, boolean acceptedPopover) throws SQLException {
        try (var stmt = connection.prepareStatement("""
                INSERT OR REPLACE 
                INTO samples (domain, url, sample, requests, accepted_popover) 
                VALUES (?, ?, ?, ?, ?)
                """)) {
            stmt.setString(1, domain);
            stmt.setString(2, url);
            stmt.setString(3, sample);
            stmt.setString(4, requests);
            stmt.setBoolean(5, acceptedPopover);
            stmt.executeUpdate();
        }
    }

    public void close() throws SQLException {
        connection.close();
    }
}
