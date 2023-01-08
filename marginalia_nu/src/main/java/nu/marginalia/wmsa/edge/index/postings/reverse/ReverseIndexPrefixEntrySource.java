package nu.marginalia.wmsa.edge.index.postings.reverse;

import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.wmsa.edge.index.query.EntrySource;

import static java.lang.Math.min;

public class ReverseIndexPrefixEntrySource implements EntrySource {
    private final BTreeReader reader;

    int pos;
    long endOffset;

    public ReverseIndexPrefixEntrySource(BTreeReader reader, long prefixStart, long prefixEnd) {
        this.reader = reader;

        pos = 0;
        endOffset = pos + (long) reader.numEntries();
    }

    @Override
    public void skip(int n) {
        pos += n;
    }

    @Override
    public void read(LongQueryBuffer buffer) {
        buffer.end = min(buffer.end, (int)(endOffset - pos));

        reader.readData(buffer.data, buffer.end, pos);

        pos += buffer.end;

        buffer.uniq();
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
