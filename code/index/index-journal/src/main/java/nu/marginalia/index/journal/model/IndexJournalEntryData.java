package nu.marginalia.index.journal.model;

import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.model.idx.WordMetadata;

import java.util.Arrays;
import java.util.Iterator;

/** The keyword data of an index journal entry.
 *  The data itself is an interleaved array of
 *  word ids and metadata.
 * <p>
 *  Odd entries are term ids, even entries are encoded WordMetadata records.
 *  </p>
 *  <p>The civilized way of reading the journal data is to use an IndexJournalReader</p>
 *
 * @see WordMetadata
 * @see IndexJournalReader
 */
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
            throw new ArrayIndexOutOfBoundsException(idx + " vs " + size);
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

            return new Record(underlyingArray[pos], underlyingArray[pos+1]);
        }
    }

    public record Record(long wordId, long metadata) {}
}
