package nu.marginalia.wmsa.edge.index.svc.query.types;

import nu.marginalia.util.btree.BTreeQueryBuffer;
import nu.marginalia.util.multimap.MultimapFileLong;

import static java.lang.Math.min;
import static nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter.ENTRY_SIZE;
import static nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter.ENTRY_URL_OFFSET;

public class EntrySourceFromMapRange implements EntrySource {

    private final MultimapFileLong map;
    private long pos;
    private final long endOffset;

    public EntrySourceFromMapRange(MultimapFileLong map, long start, long end) {
        this.map = map;
        this.pos = start;
        this.endOffset = end;
    }

    @Override
    public void skip(int n) {
        pos += (long) n * ENTRY_SIZE;
    }

    @Override
    public void read(BTreeQueryBuffer buffer) {

        assert buffer.end%ENTRY_SIZE == 0;

        buffer.end = min(buffer.end, (int)(endOffset - pos));

        map.read(buffer.data, buffer.end, pos);

        pos += buffer.end;

        destagger(buffer);
        buffer.uniq();
    }

    private void destagger(BTreeQueryBuffer buffer) {
        for (int i = 0; (i + ENTRY_SIZE - 1) < buffer.end; i += ENTRY_SIZE) {
            buffer.data[i / ENTRY_SIZE] = buffer.data[i + ENTRY_URL_OFFSET];
        }

        buffer.end /= ENTRY_SIZE;
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
