package nu.marginalia.index.reverse;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.reverse.query.EntrySource;
import nu.marginalia.skiplist.SkipListReader;

public class FullIndexEntrySource implements EntrySource {
    private final String name;

    private final SkipListReader reader;
    private final long wordId;

    public FullIndexEntrySource(String name,
                                SkipListReader reader,
                                long wordId) {
        this.name = name;
        this.reader = reader;
        this.wordId = wordId;
    }

    @Override
    public void read(LongQueryBuffer buffer) {
        reader.getKeys(buffer);
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
