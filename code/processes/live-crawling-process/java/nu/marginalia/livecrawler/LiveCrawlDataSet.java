package nu.marginalia.livecrawler;

import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.model.crawldata.SerializableCrawlData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Data access object for the live crawl database, a simple sqlite file */
public class LiveCrawlDataSet implements AutoCloseable {
    private final Connection connection;
    private final Path basePath;

    public LiveCrawlDataSet(Path basePath) throws SQLException {
        this.basePath = basePath;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + basePath.resolve("live-crawl-data.db"));
        this.connection.setAutoCommit(true);

        try (var stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS urls (url TEXT PRIMARY KEY, domainId LONG, body BLOB, headers BLOB, ip TEXT, timestamp long)");
            stmt.execute("CREATE INDEX IF NOT EXISTS domainIdIndex ON urls (domainId)");
            stmt.execute("CREATE TABLE IF NOT EXISTS badUrls (url TEXT PRIMARY KEY, timestamp long)");
        }
    }

    public Path createWorkDir() throws IOException {
        return Files.createTempDirectory(basePath, "work");
    }

    /** Remove entries older than the given timestamp */
    public void prune(Instant cutoff) throws SQLException {
        try (var stmt = connection.prepareStatement("DELETE FROM urls WHERE timestamp < ?")) {
            stmt.setLong(1, cutoff.toEpochMilli());
            stmt.executeUpdate();
        }

        try (var stmt = connection.prepareStatement("DELETE FROM badUrls WHERE timestamp < ?")) {
            stmt.setLong(1, cutoff.toEpochMilli());
            stmt.executeUpdate();
        }
    }

    /** Check if the given URL is already in the database */
    public boolean hasUrl(String url) throws SQLException {
        try (var stmt = connection.prepareStatement("""
                SELECT 1 FROM urls WHERE urls.url = ?
                UNION
                SELECT 1 FROM badUrls WHERE badUrls.url = ?
                """);
        ) {
            stmt.setString(1, url);
            stmt.setString(2, url);

            return stmt.executeQuery().next();
        }
    }

    /** Check if the given URL is already in the database */
    public boolean hasUrl(EdgeUrl url) throws SQLException {
        return hasUrl(url.toString());
    }

    /** Save a document to the database */
    public void saveDocument(int domainId, EdgeUrl url, String body, String headers, String ip) throws SQLException, IOException {
        try (var stmt = connection.prepareStatement("""
                INSERT OR REPLACE INTO urls (domainId, url, body, headers, ip, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                """))
        {
            stmt.setInt(1, domainId);
            stmt.setString(2, url.toString());
            stmt.setBytes(3, compress(body));
            stmt.setBytes(4, compress(headers));
            stmt.setString(5, ip);
            stmt.setLong(6, Instant.now().toEpochMilli());
            stmt.executeUpdate();
        }
    }

    /** Flag a URL as bad, i.e. it should not be revisited */
    public void flagAsBad(EdgeUrl url) {
        try (var stmt = connection.prepareStatement("""
                INSERT OR IGNORE INTO badUrls (url, timestamp)
                VALUES (?, ?)
                """))
        {
            stmt.setString(1, url.toString());
            stmt.setLong(2, Instant.now().toEpochMilli());
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private byte[] compress(String data) throws IOException {
        // gzip compression
        try (var bos = new ByteArrayOutputStream();
             var gzip = new GZIPOutputStream(bos))
        {
            gzip.write(data.getBytes());
            gzip.finish();
            return bos.toByteArray();
        }
    }

    private String decompress(byte[] data) {
        // gzip decompression
        try (var bis = new ByteArrayInputStream(data);
             var gzip = new GZIPInputStream(bis))
        {
            return new String(gzip.readAllBytes());
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Get the data in the database as a list of SerializableCrawlDataStream's, the
     * format expected by the converter code.
     */
    public Collection<SerializableCrawlDataStream> getDataStreams() throws SQLException {
        List<Integer> domainIds = new ArrayList<>();

        try (var stmt = connection.createStatement()) {
            var rs = stmt.executeQuery("SELECT DISTINCT domainId FROM urls");
            while (rs.next()) {
                domainIds.add(rs.getInt(1));
            }
        }

        List<SerializableCrawlDataStream> streams = new ArrayList<>();
        for (var domainId : domainIds) {
            streams.add(new WrappedDataStream(domainId));
        }
        return streams;
    }

    /** Wraps the data in the database as a SerializableCrawlDataStream.
     * <p></p>
     * This is a bit clunky as the interface is built intending the data
     * to be a stream of objects being read from Parquet.
     * */
    private class WrappedDataStream implements SerializableCrawlDataStream {
        private final int domainId;
        private ArrayList<SerializableCrawlData> dataStack;

        WrappedDataStream(int domainId) {
            this.domainId = domainId;
            this.dataStack = null;
        }

        /** Lazy initialization for the data being iterated over */
        private void query() {
            try (var stmt = connection.prepareStatement("""
                                                SELECT url, body, headers, ip, timestamp
                                                FROM urls
                                                WHERE domainId = ?
                                                """)) {
                stmt.setInt(1, domainId);
                var rs = stmt.executeQuery();
                dataStack = new ArrayList<>();
                while (rs.next()) {
                    String url = rs.getString("url");
                    String body = decompress(rs.getBytes("body"));
                    String headers = decompress(rs.getBytes("headers"));

                    dataStack.add(new CrawledDocument(
                            "LIVE",
                            url,
                            "text/html",
                            Instant.ofEpochMilli(rs.getLong("timestamp")).toString(),
                            200,
                            "OK",
                            "",
                            headers,
                            body,
                            false,
                            "",
                            ""
                    ));
                }
                var last = dataStack.getLast();
                var domain = new CrawledDomain(
                        last.getDomain(),
                        null,
                        "OK",
                        "",
                        "0.0.0.0",
                        List.of(),
                        List.of()
                );

                // Add the domain as the last element, which will be the first
                // element popped from the list
                dataStack.addLast(domain);
            }
            catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public SerializableCrawlData next() throws IOException {
            if (dataStack == null)
                query();

            return dataStack.removeLast();
        }

        @Override
        public boolean hasNext() throws IOException {
            if (dataStack == null) {
                query();
            }
            return !dataStack.isEmpty();
        }

        @Override
        public void close() throws Exception {
            dataStack.clear();
        }
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
