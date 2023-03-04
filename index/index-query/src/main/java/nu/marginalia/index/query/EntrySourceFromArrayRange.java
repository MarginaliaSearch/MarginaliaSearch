package nu.marginalia.index.query;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.buffer.LongQueryBuffer;

import static java.lang.Math.min;

public class EntrySourceFromArrayRange implements EntrySource {

    private final LongArray map;
    private final int entrySize;
    private long pos;
    private final long endOffset;

    public EntrySourceFromArrayRange(LongArray map, int entrySize, long start, long end) {
        this.map = map;
        this.entrySize = entrySize;
        this.pos = start;
        this.endOffset = end;
    }

    @Override
    public void skip(int n) {
        pos += (long) n * entrySize;
    }

    @Override
    public void read(LongQueryBuffer buffer) {

        assert buffer.end%entrySize == 0;

        buffer.end = min(buffer.end, (int)(endOffset - pos));

        map.get(pos, pos + buffer.end, buffer.data);

        pos += buffer.end;

        destagger(buffer);
        buffer.uniq();
    }

    private void destagger(LongQueryBuffer buffer) {
        if (entrySize == 1)
            return;

        for (int i = 0; (i + entrySize - 1) < buffer.end; i += entrySize) {
            buffer.data[i / entrySize] = buffer.data[i + entrySize];
        }

        buffer.end /= entrySize;
    }

    @Override
    public boolean hasMore() {
        return pos < endOffset;
    }

    @Override
    public String toString() {
        return String.format("BTreeRange.EntrySourceFromMapRange(@" + pos + ": " + endOffset + ")");
    }

}
