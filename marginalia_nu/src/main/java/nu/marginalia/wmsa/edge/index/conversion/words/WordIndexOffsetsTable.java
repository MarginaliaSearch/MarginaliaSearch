package nu.marginalia.wmsa.edge.index.conversion.words;

import java.io.IOException;

public class WordIndexOffsetsTable {
    final long[] table;
    public final int numberOfUsedWords;

    public WordIndexOffsetsTable(long[] table, int numberOfUsedWords) {

        this.table = table;
        this.numberOfUsedWords = numberOfUsedWords;
    }

    public int length() {
        return table.length;
    }

    public void forEachRange(OffsetTableEntryConsumer o) throws IOException {
        if (table[0] > 0) {
            o.accept(0, (int) table[0]);
        }

        for (int i = 1; i < table.length; i++) {
            long start = table[i-1];
            long end = table[i];

            if (start != end) {
                o.accept(start, end);
            }
        }
    }

    /**
     * Fold over each span in the file, left to right, accumulating the return value
     */
    public long foldRanges(OffsetTableEntryFoldConsumer o) throws IOException {
        long total = 0;

        if (table[0] > 0) {
            total = o.accept(total,0, (int) table[0]);
        }

        for (int i = 1; i < table.length; i++) {
            long start = table[i-1];
            int length = (int) (table[i] - start);

            if (length != 0) {
                total += o.accept(total, start, length);
            }
        }

        return total;
    }

    public long get(int i) {
        return table[i];
    }

    public interface OffsetTableEntryConsumer {
        void accept(long start, long end) throws IOException;
    }

    public interface OffsetTableEntryFoldConsumer {
        long accept(long accumulator, long start, int length) throws IOException;
    }
}
