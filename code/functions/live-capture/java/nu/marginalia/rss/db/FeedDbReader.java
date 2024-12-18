package nu.marginalia.rss.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.rss.model.FeedDefinition;
import nu.marginalia.rss.model.FeedItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

public class FeedDbReader implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FeedDbReader.class);
    private static final Gson gson = new GsonBuilder().create();
    private final Connection connection;

    public FeedDbReader(Path dbPath) throws SQLException {
        String dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        logger.info("Opening feed db at " + dbUrl);

        connection = DriverManager.getConnection(dbUrl);

        // Create table if it doesn't exist to avoid errors before any feeds have been fetched
        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS feed (domain TEXT PRIMARY KEY, feed JSON)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS errors (domain TEXT PRIMARY KEY, cnt INT DEFAULT 0)");
        }
    }

    public List<FeedDefinition> getAllFeeds() {
        List<FeedDefinition> ret = new ArrayList<>();

        try (var stmt = connection.createStatement()) {
            var rs = stmt.executeQuery("""
                select
                    json_extract(feed, '$.domain') as domain,
                    json_extract(feed, '$.feedUrl') as url,
                    json_extract(feed, '$.updated') as updated
                from feed
                """);

            while (rs.next()) {
                ret.add(new FeedDefinition(
                        rs.getString("domain"),
                        rs.getString("url"),
                        rs.getString("updated")));
            }

        } catch (SQLException e) {
            logger.error("Error getting all feeds", e);
        }

        return ret;
    }

    public Optional<String> getFeedAsJson(String domain) {
        try (var stmt = connection.prepareStatement("SELECT FEED FROM feed WHERE DOMAIN = ?")) {
            stmt.setString(1, domain);

            var rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(rs.getString(1));
            }
        } catch (SQLException e) {
            logger.error("Error getting feed for " + domain, e);
        }
        return Optional.empty();
    }

    public Map<String, Integer> getAllErrorCounts() {
        Map<String, Integer> ret = new HashMap<>(100_000);

        try (var stmt = connection.prepareStatement("SELECT domain, cnt FROM errors")) {

            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            logger.error("Error getting errors", e);
        }

        return ret;
    }

    public FeedItems getFeed(EdgeDomain domain) {
        try (var stmt = connection.prepareStatement("SELECT FEED FROM feed WHERE DOMAIN = ?")) {
            stmt.setString(1, domain.toString());
            var rs = stmt.executeQuery();

            if (rs.next()) {
                return deserialize(rs.getString(1));
            }
        } catch (SQLException e) {
            logger.error("Error getting feed for " + domain, e);
        }

        return FeedItems.none();
    }

    private FeedItems deserialize(String string) {
        return gson.fromJson(string, FeedItems.class);
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }


    public void getLinksUpdatedSince(Instant since, BiConsumer<String, List<String>> consumer) {
        try (var stmt = connection.prepareStatement("SELECT FEED FROM feed")) {
            var rs = stmt.executeQuery();

            while (rs.next()) {
                FeedItems items = deserialize(rs.getString(1));

                List<String> urls = new ArrayList<>();
                for (var item : items.items()) {
                    if (item.getUpdateTimeZD().toInstant().isAfter(since)) {
                        urls.add(item.url());
                    }
                }

                if (!urls.isEmpty()) {
                    consumer.accept(items.domain(), new ArrayList<>(urls));
                    urls.clear();
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting updated links", e);
        }
    }


}
