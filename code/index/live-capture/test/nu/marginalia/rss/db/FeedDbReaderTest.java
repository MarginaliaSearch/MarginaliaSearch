package nu.marginalia.rss.db;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FeedDbReaderTest {

    @Tag("flaky") // will only work on ~vlofgren, not on CI; remove test  when this feature is stable
    @Test
    void getLinksUpdatedSince() throws SQLException {
        var reader = new FeedDbReader(Path.of("/home/vlofgren/rss-feeds.db"));
        Map<String, List<String>> links = new HashMap<>();

        reader.getLinksUpdatedSince(Instant.now().minus(10, ChronoUnit.DAYS), links::put);

        System.out.println(links.size());
        for (var link : links.values()) {
            if (link.size() < 2) {
                System.out.println(link);
            }
        }

        reader.close();

    }
}