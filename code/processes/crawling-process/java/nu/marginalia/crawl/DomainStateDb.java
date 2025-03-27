package nu.marginalia.crawl;

import com.google.inject.Inject;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Supplemental sqlite database for storing the summary of a crawl.
 *  One database exists per crawl data set.
 * */
public class DomainStateDb implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DomainStateDb.class);

    private final Connection connection;


    public record CrawlMeta(
            String domainName,
            Instant lastFullCrawl,
            Duration recrawlTime,
            Duration crawlTime,
            int recrawlErrors,
            int crawlChanges,
            int totalCrawlSize
    ) {}

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

    public record FaviconRecord(String contentType, byte[] imageData) {}

    @Inject
    public DomainStateDb(FileStorageService fileStorageService) throws SQLException {
        this(findFilename(fileStorageService));
    }

    private static Path findFilename(FileStorageService fileStorageService) throws SQLException {
        var fsId = fileStorageService.getOnlyActiveFileStorage(FileStorageType.CRAWL_DATA);

        if (fsId.isPresent()) {
            var fs = fileStorageService.getStorage(fsId.get());
            return fs.asPath().resolve("domainstate.db");
        }
        else {
            return null;
        }
    }

    public DomainStateDb(@Nullable Path filename) throws SQLException {
        if (null == filename) {
            connection = null;
            return;
        }

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
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS crawl_meta (
                        domain TEXT PRIMARY KEY,
                        lastFullCrawlEpochMs LONG NOT NULL,
                        recrawlTimeMs LONG NOT NULL,
                        recrawlErrors INTEGER NOT NULL,
                        crawlTimeMs LONG NOT NULL,
                        crawlChanges INTEGER NOT NULL,
                        totalCrawlSize INTEGER NOT NULL
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS favicon (
                        domain TEXT PRIMARY KEY,
                        contentType TEXT NOT NULL,
                        icon BLOB NOT NULL
                    )
                    """);
            stmt.execute("PRAGMA journal_mode=WAL");
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isAvailable() {
        return connection != null;
    }

    public void saveIcon(String domain, FaviconRecord faviconRecord) {
        if (connection == null) throw new IllegalStateException("No connection to domainstate db");

        try (var stmt = connection.prepareStatement("""
                INSERT OR REPLACE INTO favicon (domain, contentType, icon)
                       VALUES(?, ?, ?)
            """)) {
            stmt.setString(1, domain);
            stmt.setString(2, Objects.requireNonNullElse(faviconRecord.contentType, "application/octet-stream"));
            stmt.setBytes(3, faviconRecord.imageData);
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            logger.error("Failed to insert favicon", ex);
        }
    }

    public Optional<FaviconRecord> getIcon(String domain) {
        if (connection == null)
            return Optional.empty();

        try (var stmt = connection.prepareStatement("SELECT contentType, icon FROM favicon WHERE DOMAIN = ?")) {
            stmt.setString(1, domain);
            var rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(
                    new FaviconRecord(
                        rs.getString("contentType"),
                        rs.getBytes("icon")
                    )
                );
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve favicon", e);
        }

        return Optional.empty();
    }

    public void save(CrawlMeta crawlMeta) {
        if (connection == null) throw new IllegalStateException("No connection to domainstate db");

        try (var stmt = connection.prepareStatement("""
                INSERT OR REPLACE INTO crawl_meta (domain, lastFullCrawlEpochMs, recrawlTimeMs, recrawlErrors, crawlTimeMs, crawlChanges, totalCrawlSize)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            stmt.setString(1, crawlMeta.domainName());
            stmt.setLong(2, crawlMeta.lastFullCrawl.toEpochMilli());
            stmt.setLong(3, crawlMeta.recrawlTime.toMillis());
            stmt.setInt(4, crawlMeta.recrawlErrors);
            stmt.setLong(5, crawlMeta.crawlTime.toMillis());
            stmt.setInt(6, crawlMeta.crawlChanges);
            stmt.setInt(7, crawlMeta.totalCrawlSize);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert crawl meta record", e);
        }
    }

    public void save(SummaryRecord record) {
        if (connection == null) throw new IllegalStateException("No connection to domainstate db");

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

    public Optional<CrawlMeta> getMeta(String domainName) {
        if (connection == null)
            return Optional.empty();

        try (var stmt = connection.prepareStatement("""
                SELECT domain, lastFullCrawlEpochMs, recrawlTimeMs, recrawlErrors, crawlTimeMs, crawlChanges, totalCrawlSize
                FROM crawl_meta
                WHERE domain = ?
                """)) {
            stmt.setString(1, domainName);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new CrawlMeta(
                        rs.getString("domain"),
                        Instant.ofEpochMilli(rs.getLong("lastFullCrawlEpochMs")),
                        Duration.ofMillis(rs.getLong("recrawlTimeMs")),
                        Duration.ofMillis(rs.getLong("crawlTimeMs")),
                        rs.getInt("recrawlErrors"),
                        rs.getInt("crawlChanges"),
                        rs.getInt("totalCrawlSize")
                ));
            }
        } catch (SQLException ex) {
            logger.error("Failed to get crawl meta record", ex);
        }
        return Optional.empty();
    }

    public Optional<SummaryRecord> getSummary(String domainName) {
        if (connection == null)
            return Optional.empty();

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
