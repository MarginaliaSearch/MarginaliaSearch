package nu.marginalia.index.reverse.query;

import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EntrySource;

import static java.lang.Math.min;

public class ReverseIndexEntrySource implements EntrySource {
    private final BTreeReader reader;

    private static final int ENTRY_SIZE = 2;

    int pos;
    int endOffset;

    private final ReverseIndexEntrySourceBehavior behavior;

    public ReverseIndexEntrySource(BTreeReader reader, ReverseIndexEntrySourceBehavior behavior) {
        this.reader = reader;
        this.behavior = behavior;

        pos = 0;
        endOffset = pos + ENTRY_SIZE*reader.numEntries();
    }

    @Override
    public void skip(int n) {
        pos += n;
    }

    @Override
    public void read(LongQueryBuffer buffer) {
        if (behavior == ReverseIndexEntrySourceBehavior.DO_NOT_PREFER
                && buffer.hasRetainedData())
        {
            pos = endOffset;
            return;
        }

        buffer.end = min(buffer.end, endOffset - pos);

        reader.readData(buffer.data, buffer.end, pos);

        pos += buffer.end;

        destagger(buffer);
        buffer.uniq();
    }

    private void destagger(LongQueryBuffer buffer) {
        if (ENTRY_SIZE == 1)
            return;

        for (int ri = ENTRY_SIZE, wi=1; ri < buffer.end ; ri+=ENTRY_SIZE, wi++) {
            buffer.data[wi] = buffer.data[ri];
        }

        buffer.end /= ENTRY_SIZE;
    }

    @Override
    public boolean hasMore() {
        return pos < endOffset;
    }

    @Override
    public String toString() {
        return String.format("BTreeRange.EntrySource(@" + pos + ": " + endOffset + ")");
    }

}
