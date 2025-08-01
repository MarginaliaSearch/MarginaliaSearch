package nu.marginalia.index;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.skiplist.SkipListReader;

public class FullIndexEntrySource implements EntrySource {
    private final String name;

    int pos;
    int endOffset;

    private final SkipListReader reader;
    private final long wordId;

    public FullIndexEntrySource(String name,
                                SkipListReader reader,
                                long wordId) {
        this.name = name;
        this.reader = reader;
        this.wordId = wordId;

        pos = 0;
    }

    @Override
    public void skip(int n) {
        pos += n;
    }

    @Override
    public void read(LongQueryBuffer buffer) {
        reader.getData(buffer);

        // Is this needed?
        buffer.uniq();
    }

    @Override
    public boolean hasMore() {
        return !reader.atEnd();
    }

    @Override
    public String indexName() {
        return name + ":" + Long.toHexString(wordId);
    }
}
