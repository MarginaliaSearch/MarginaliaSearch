package nu.marginalia.index.construction.prio;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.journal.IndexJournalPage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A pair of file-backed arrays of sorted wordIds
 * and the count of documents associated with each termId.
 */
public class PrioPreindexWordSegments {
    public final LongArray wordIds;
    public final LongArray counts;

    final Path wordsFile;
    final Path countsFile;

    public PrioPreindexWordSegments(LongArray wordIds,
                                    LongArray counts,
                                    Path wordsFile,
                                    Path countsFile)
    {
        assert wordIds.size() == counts.size();

        this.wordIds = wordIds;
        this.counts = counts;
        this.wordsFile = wordsFile;
        this.countsFile = countsFile;
    }

    /** Returns a long-long hash map where each key is a termId,
     * and each value is the start offset of the data.
     */
    public Long2LongOpenHashMap asMap(int recordSize) {
        if (wordIds.size() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Cannot create a map with more than Integer.MAX_VALUE entries");

        Long2LongOpenHashMap ret = new Long2LongOpenHashMap((int) wordIds.size(), 0.75f);
        var iter = iterator(recordSize);

        while (iter.next()) {
            ret.put(iter.wordId, iter.startOffset);
        }

        return ret;
    }

    public static PrioPreindexWordSegments construct(IndexJournalPage journalInstance,
                                                     Path wordIdsFile,
                                                     Path countsFile)
    throws IOException
    {
        Long2IntOpenHashMap countsMap = new Long2IntOpenHashMap(100_000, 0.75f);
        countsMap.defaultReturnValue(0);

        try (var termIds = journalInstance.openTermIds();
             var termMetas = journalInstance.openTermMetadata()) {

            while (termIds.hasRemaining()) {
                long data = termIds.get();
                byte meta = termMetas.get();

                if (meta != 0) {
                    countsMap.addTo(data, 1);
                }
            }
        }

        LongArray words = LongArrayFactory.mmapForWritingConfined(wordIdsFile, countsMap.size());
        LongArray counts = LongArrayFactory.mmapForWritingConfined(countsFile, countsMap.size());

        // Create the words file by iterating over the map and inserting them into
        // the words file in whatever bizarro hash table order they appear in
        long i = 0;
        LongIterator iter = countsMap.keySet().iterator();
        while (iter.hasNext()) {
            words.set(i++, iter.nextLong());
        }

        // Sort the words file
        words.sort(0, counts.size());

        // Populate the counts
        for (i = 0; i < countsMap.size(); i++) {
            counts.set(i, countsMap.get(words.get(i)));
        }

        return new PrioPreindexWordSegments(words, counts, wordIdsFile, countsFile);
    }

    public SegmentIterator iterator(int recordSize) {
        return new SegmentIterator(recordSize);
    }
    public SegmentConstructionIterator constructionIterator(int recordSize) {
        return new SegmentConstructionIterator(recordSize);
    }

    public long totalSize() {
        return counts.fold(0, 0, counts.size(), Long::sum);
    }

    public void delete() throws IOException {
        Files.delete(countsFile);
        Files.delete(wordsFile);

        counts.close();
        wordIds.close();
    }

    public void force() {
        counts.force();
        wordIds.force();
    }

    public void close() {
        wordIds.close();
        counts.close();
    }

    public class SegmentIterator {
        private final int recordSize;
        private final long fileSize;
        long wordId;
        long startOffset = 0;
        long endOffset = 0;

        private SegmentIterator(int recordSize) {
            this.recordSize = recordSize;
            this.fileSize = wordIds.size();
        }

        private long i = -1;
        public long idx() {
            return i;
        }
        public boolean next() {
            if (++i >= fileSize) {
                wordId = Long.MIN_VALUE;
                return false;
            }

            wordId = wordIds.get(i);
            startOffset = endOffset;
            endOffset = startOffset + recordSize * counts.get(i);

            return true;
        }

        public boolean hasMorePositions() {
            return i + 1 < wordIds.size();
        }

        public boolean isPositionBeforeEnd() {
            return i < wordIds.size();
        }

        public long size() {
            return endOffset - startOffset;
        }
    }

    class SegmentConstructionIterator {
        private final int recordSize;
        private final long fileSize;
        long wordId;
        long startOffset = 0;
        long endOffset = 0;

        private SegmentConstructionIterator(int recordSize) {
            this.recordSize = recordSize;
            this.fileSize = wordIds.size();
            if (fileSize == 0) {
                throw new IllegalArgumentException("Cannot construct zero-length word segment file");
            }
            this.wordId = wordIds.get(0);
        }

        private long i = 0;
        public long idx() {
            return i;
        }

        public boolean putNext(long size) {

            if (i >= fileSize)
                return false;

            endOffset = startOffset + recordSize * size;
            counts.set(i, size);
            startOffset = endOffset;
            endOffset = -1;

            i++;

            if (i == fileSize) {
                // We've reached the end of the iteration and there is no
                // "next" termId to fetch
                wordId = Long.MIN_VALUE;
                return false;
            }
            else {
                wordId = wordIds.get(i);
                return true;
            }
        }

        public boolean canPutMore() {
            return i < wordIds.size();
        }
    }
}
