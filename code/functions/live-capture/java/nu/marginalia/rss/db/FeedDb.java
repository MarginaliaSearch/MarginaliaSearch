package nu.marginalia.rss.db;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.rss.model.FeedDefinition;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.module.ServiceConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Singleton
public class FeedDb {
    private static final Logger logger = LoggerFactory.getLogger(FeedDb.class);

    private static final String dbFileName = "rss-feeds.db";

    private final Path readerDbPath;
    private volatile FeedDbReader reader;

    private final boolean feedDbEnabled;

    @Inject
    public FeedDb(ServiceConfiguration serviceConfiguration) {
        feedDbEnabled = serviceConfiguration.node() <= 1;
        readerDbPath = WmsaHome.getDataPath().resolve(dbFileName);

        if (!feedDbEnabled) {
            logger.info("Feed database is disabled on this node");
        }
        else {
            try {
                reader = new FeedDbReader(readerDbPath);
            } catch (Exception e) {
                reader = null;
            }
        }
    }

    /** Constructor for testing */
    public FeedDb(Path dbPath) {
        feedDbEnabled = true;
        readerDbPath = dbPath;

        try {
            reader = new FeedDbReader(readerDbPath);
        } catch (Exception e) {
            reader = null;
        }
    }

    public boolean isEnabled() {
        return feedDbEnabled;
    }

    public List<FeedDefinition> getAllFeeds() {
        if (!feedDbEnabled) {
            throw new IllegalStateException("Feed database is disabled on this node");
        }

        // Capture the current reader to avoid concurrency issues
        FeedDbReader reader = this.reader;

        try {
            if (reader != null) {
                return reader.getAllFeeds();
            }
        }
        catch (Exception e) {
            logger.error("Error getting all feeds", e);
        }
        return List.of();
    }

    @NotNull
    public FeedItems getFeed(EdgeDomain domain) {
        if (!feedDbEnabled) {
            throw new IllegalStateException("Feed database is disabled on this node");
        }

        // Capture the current reader to avoid concurrency issues
        FeedDbReader reader = this.reader;
        try {
            if (reader != null) {
                return reader.getFeed(domain);
            }
        }
        catch (Exception e) {
            logger.error("Error getting feed for " + domain, e);
        }
        return FeedItems.none();
    }

    public Optional<String> getFeedAsJson(String domain) {
        if (!feedDbEnabled) {
            throw new IllegalStateException("Feed database is disabled on this node");
        }

        // Capture the current reader to avoid concurrency issues
        FeedDbReader reader = this.reader;

        try {
            if (reader != null) {
                return reader.getFeedAsJson(domain);
            }
        }
        catch (Exception e) {
            logger.error("Error getting feed for " + domain, e);
        }
        return Optional.empty();
    }

    public FeedDbWriter createWriter() {
        if (!feedDbEnabled) {
            throw new IllegalStateException("Feed database is disabled on this node");
        }

        try {
            Path dbFile = Files.createTempFile(readerDbPath.getParent(), "rss-feeds", ".tmp.db");
            return new FeedDbWriter(dbFile);
        } catch (Exception e) {
            logger.error("Error creating new database writer", e);
            return null;
        }
    }

    public void switchDb(FeedDbWriter writer) {
        if (!feedDbEnabled) {
            throw new IllegalStateException("Feed database is disabled on this node");
        }

        try {
            logger.info("Switching to new feed database from " + writer.getDbPath() + " to " + readerDbPath);

            writer.close();
            reader.close();

            Files.move(writer.getDbPath(), readerDbPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            reader = new FeedDbReader(readerDbPath);
        } catch (Exception e) {
            logger.error("Fatal error switching to new feed database", e);

            reader = null;
        }
    }

    public String getDataHash() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");

        byte[] buffer = new byte[4096];

        try (var inputStream = new BufferedInputStream(Files.newInputStream(readerDbPath))) {
            int rb;

            while ((rb = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, rb);
            }
        }

        return Base64.getEncoder().encodeToString(digest.digest());
    }
}
