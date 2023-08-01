package nu.marginalia.index.journal.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;

public class IndexJournalEntryData implements Iterable<IndexJournalEntryData.Record> {
    private final int size;
    public final long[] underlyingArray;

    public static final int MAX_LENGTH = 1000;
    public static final int ENTRY_SIZE = 2;

    public IndexJournalEntryData(long[] underlyingArray) {
        this.size = underlyingArray.length;
        this.underlyingArray = underlyingArray;
    }

    public IndexJournalEntryData(int size, long[] underlyingArray) {
        this.size = size;
        this.underlyingArray = underlyingArray;
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

    public Iterator<Record> iterator() {
        return new EntryIterator();
    }

    private class EntryIterator implements Iterator<Record> {
        int pos = -ENTRY_SIZE;

        public boolean hasNext() {
            return pos + 2*ENTRY_SIZE - 1 < size;
        }

        @Override
        public Record next() {
            pos+=ENTRY_SIZE;

            return new Record((int) underlyingArray[pos], underlyingArray[pos+1]);
        }
    }

    public record Record(int wordId, long metadata) {}
}
