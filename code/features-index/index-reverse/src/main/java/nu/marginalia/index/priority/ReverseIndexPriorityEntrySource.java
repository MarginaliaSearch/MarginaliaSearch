package nu.marginalia.index.priority;

import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.index.query.ReverseIndexEntrySourceBehavior;

import static java.lang.Math.min;

public class ReverseIndexPriorityEntrySource implements EntrySource {
    private final BTreeReader reader;

    int pos;
    int endOffset;

    private final ReverseIndexEntrySourceBehavior behavior;
    private final int wordId;

    public ReverseIndexPriorityEntrySource(BTreeReader reader, ReverseIndexEntrySourceBehavior behavior, int wordId) {
        this.reader = reader;
        this.behavior = behavior;
        this.wordId = wordId;

        pos = 0;
        endOffset = pos + reader.numEntries();
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

        buffer.uniq();
    }

    @Override
    public boolean hasMore() {
        return pos < endOffset;
    }

    @Override
    public String indexName() {
        return "Priority:" + wordId;
    }
}
