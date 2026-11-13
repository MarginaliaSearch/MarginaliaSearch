package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArray;

/** Sorted term ids and the number of postings associated with each term. */
public class FullPreindexWordSegments implements AutoCloseable {
    public final LongArray wordIds;
    public final LongArray counts;

    public FullPreindexWordSegments(LongArray wordIds, LongArray counts) {
        assert wordIds.size() == counts.size();
        this.wordIds = wordIds;
        this.counts = counts;
    }

    public SegmentIterator iterator(int recordSize) {
        return new SegmentIterator(recordSize);
    }

    @Override
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

        public long size() {
            return endOffset - startOffset;
        }
    }
}
