package nu.marginalia.rss.db;

import nu.marginalia.rss.model.FeedItem;
import nu.marginalia.rss.model.FeedItems;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class FeedDbTest {

    @Test
    public void testErrorCounts() throws Exception {
        Path dbPath = Files.createTempFile("rss-feeds", ".db");

        try {
            FeedDb db = new FeedDb(dbPath);

            try (var writer = db.createWriter()) {
                writer.setErrorCount("foo", 1);
                writer.setErrorCount("bar", 5);
                db.switchDb(writer);
            }

            Map<String, Integer> allErrorCounts = db.getAllErrorCounts();
            Assertions.assertEquals(Map.of("foo", 1, "bar", 5), allErrorCounts);
        } finally {
            Files.delete(dbPath);
        }
    }

    @Test
    public void testDbHash() throws Exception{
        Path dbPath = Files.createTempFile("rss-feeds", ".db");
        try {
            FeedDb db = new FeedDb(dbPath);

            try (var writer = db.createWriter()) {
                writer.saveFeed(new FeedItems("foo", "bar", "baz", List.of(
                        new FeedItem("title1", "link1", "description1", "content1"),
                        new FeedItem("title2", "link2", "description2", "content2")
                )));

                db.switchDb(writer);
            }

            var hash = db.getDataHash();
            Assertions.assertFalse(hash.isBlank());
        } finally {
            Files.delete(dbPath);
        }
    }
}