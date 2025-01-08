package nu.marginalia.rss.svc;

import nu.marginalia.WmsaHome;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.string.StringColumn;
import nu.marginalia.slop.desc.StorageType;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Utility for recording fetched feeds to a journal, useful in debugging feed parser issues.
 */
public interface FeedJournal extends AutoCloseable {
    StringColumn urlColumn = new StringColumn("url");
    StringColumn contentsColumn = new StringColumn("contents", StandardCharsets.UTF_8, StorageType.ZSTD);

    void record(String url, String contents) throws IOException;
    void close() throws IOException;


    static FeedJournal create() throws IOException {
        if (Boolean.getBoolean("feedFetcher.persistJournal")) {
            Path journalPath = WmsaHome.getDataPath().resolve("feed-journal");
            if (Files.isDirectory(journalPath)) {
                FileUtils.deleteDirectory(journalPath.toFile());
            }
            Files.createDirectories(journalPath);
            return new RecordingFeedJournal(journalPath);
        }
        else {
            return new NoOpFeedJournal();
        }
    }

    class NoOpFeedJournal implements FeedJournal {
        @Override
        public void record(String url, String contents) {}

        @Override
        public void close() {}
    }

    class RecordingFeedJournal extends SlopTable implements FeedJournal {

        private final StringColumn.Writer urlWriter;
        private final StringColumn.Writer contentsWriter;

        public RecordingFeedJournal(Path path) throws IOException {
            super(path, SlopTable.getNumPages(path, FeedJournal.urlColumn));

            urlWriter = urlColumn.create(this);
            contentsWriter = contentsColumn.create(this);
        }

        public synchronized void record(String url, String contents) throws IOException {
            urlWriter.put(url);
            contentsWriter.put(contents);
        }

    }
}
