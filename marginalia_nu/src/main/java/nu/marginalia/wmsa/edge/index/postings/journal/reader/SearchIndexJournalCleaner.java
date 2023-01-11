package nu.marginalia.wmsa.edge.index.postings.journal.reader;

import nu.marginalia.util.array.LongArray;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.function.Predicate;

public class SearchIndexJournalCleaner {
    private final SearchIndexJournalReader reader;

    public SearchIndexJournalCleaner(SearchIndexJournalReader reader) {
        this.reader = reader;
    }

    private long dryRunForNewSize(Predicate<SearchIndexJournalReadEntry> entryPredicate) {
        long pos = SearchIndexJournalReader.FILE_HEADER_SIZE_LONGS;

        var pt = new ProgressTracker();

        for (var entry : reader) {
            if (entryPredicate.test(entry)) {
                pos += entry.totalEntrySizeLongs();
                pt.update(pos);
            }
        }

        return pos;
    }

    public void clean(Path outFile, Predicate<SearchIndexJournalReadEntry> entryPredicate) throws IOException {

        System.out.println("Dry run");
        long size = dryRunForNewSize(entryPredicate);

        System.out.println("Copying");
        LongArray outputArray = LongArray.mmapForWriting(outFile, size);

        long pos = SearchIndexJournalReader.FILE_HEADER_SIZE_LONGS;
        var pt = new ProgressTracker();

        LongBuffer adequateBuffer = ByteBuffer.allocateDirect(100*1024*1024).asLongBuffer();

        for (var entry : reader) {
            if (entryPredicate.test(entry)) {
                pos += entry.copyTo(pos, adequateBuffer, outputArray);
                pt.update(pos);
            }
        }

        outputArray.set(0, pos*8);
        outputArray.set(1, reader.fileHeader().wordCount());

        outputArray.force();
    }
}

class ProgressTracker {
    long stepSize = 100*1024*1024;
    long pos = 0;

    public void update(long pos) {
        if (this.pos / stepSize != pos / stepSize) {
            System.out.printf("%d Mb\n", (800*pos)/stepSize);
        }
        this.pos = pos;
    }

}
