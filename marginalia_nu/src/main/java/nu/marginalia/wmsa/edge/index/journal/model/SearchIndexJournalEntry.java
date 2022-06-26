package nu.marginalia.wmsa.edge.index.journal.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class SearchIndexJournalEntry {
    private final int size;
    private final long[] underlyingArray;

    public static final int MAX_LENGTH = 1000;

    public SearchIndexJournalEntry(long[] underlyingArray) {
        this.size = underlyingArray.length;
        this.underlyingArray = underlyingArray;
    }

    public SearchIndexJournalEntry(int size, long[] underlyingArray) {
        this.size = size;
        this.underlyingArray = underlyingArray;
    }

    public void write(ByteBuffer buffer) {
        for (int i = 0; i < size; i++) {
            buffer.putLong(underlyingArray[i]);
        }
    }

    public long get(int idx) {
        if (idx >= size)
            throw new ArrayIndexOutOfBoundsException();
        return underlyingArray[idx];
    }

    public int size() {
        return size;
    }

    public long[] toArray() {
        if (size == underlyingArray.length)
            return underlyingArray;
        else
            return Arrays.copyOf(underlyingArray, size);
    }

    public String toString() {
        return String.format("%s[%s]", getClass().getSimpleName(), Arrays.toString(toArray()));
    }

}
