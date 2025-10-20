package nu.marginalia.index.reverse;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.reverse.query.EntrySource;
import nu.marginalia.skiplist.SkipListReader;

public class FullIndexEntrySource implements EntrySource {
    private final String name;

    private final String term;
    private final SkipListReader reader;
    private int readEntries = 0;

    public FullIndexEntrySource(String name,
                                String term,
                                SkipListReader reader) {
        this.name = name;
        this.term = term;
        this.reader = reader;
    }

    @Override
    public void read(LongQueryBuffer buffer) {
        readEntries += reader.getKeys(buffer);
    }

    @Override
    public boolean hasMore() {
        return !reader.atEnd();
    }

    @Override
    public String indexName() {
        return name + ":" + term;
    }

    @Override
    public int readEntries() {
        return readEntries;
    }
}
