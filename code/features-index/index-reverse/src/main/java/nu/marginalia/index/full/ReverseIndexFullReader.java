package nu.marginalia.index.full;

import nu.marginalia.index.query.ReverseIndexRejectFilter;
import nu.marginalia.index.query.ReverseIndexRetainFilter;
import nu.marginalia.array.LongArray;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EmptyEntrySource;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.index.query.filter.QueryFilterLetThrough;
import nu.marginalia.index.query.filter.QueryFilterNoPass;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ReverseIndexFullReader {
    private final LongArray words;
    private final LongArray documents;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ReverseIndexFullReader(Path words, Path documents) throws IOException {
        if (!Files.exists(words) || !Files.exists(documents)) {
            this.words = null;
            this.documents = null;
            return;
        }

        logger.info("Switching reverse index");

        this.words = LongArray.mmapRead(words);
        this.documents = LongArray.mmapRead(documents);
    }

    public boolean isWordInDoc(int wordId, long documentId) {
        if (wordId < 0) {
            return false;
        }

        long offset = words.get(wordId);

        if (offset < 0) {
            return false;
        }

        return createReaderNew(offset).findEntry(documentId) >= 0;
    }

    public EntrySource documents(int wordId) {
        if (null == words) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource();
        }

        if (wordId < 0 || wordId >= words.size()) return new EmptyEntrySource();

        long offset = words.get(wordId);

        if (offset < 0) return new EmptyEntrySource();

        return new ReverseIndexFullEntrySource(createReaderNew(offset), ReverseIndexFullParameters.ENTRY_SIZE, wordId);
    }

    public QueryFilterStepIf also(int wordId) {
        if (wordId < 0) return new QueryFilterNoPass();

        long offset = words.get(wordId);

        if (offset < 0) return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(createReaderNew(offset), "full", wordId);
    }

    public QueryFilterStepIf not(int wordId) {
        if (wordId < 0) return new QueryFilterLetThrough();

        long offset = words.get(wordId);

        if (offset < 0) return new QueryFilterLetThrough();

        return new ReverseIndexRejectFilter(createReaderNew(offset));
    }

    public int numDocuments(int wordId) {
        if (wordId < 0)
            return 0;

        long offset = words.get(wordId);

        if (offset < 0)
            return 0;

        return createReaderNew(offset).numEntries();
    }

    private BTreeReader createReaderNew(long offset) {
        return new BTreeReader(documents, ReverseIndexFullParameters.bTreeContext, offset);
    }

    public long[] getTermMeta(int wordId, long[] docIds) {
        if (wordId < 0) {
            return new long[docIds.length];
        }

        long offset = words.get(wordId);
        if (offset < 0) {
            return new long[docIds.length];
        }

        assert isSorted(docIds) : "The input array docIds is assumed to be sorted";

        var reader = createReaderNew(offset);
        return reader.queryData(docIds, 1);
    }

    private boolean isSorted(long[] ids) {
        if (ids.length == 0)
            return true;
        long prev = ids[0];

        for (int i = 1; i < ids.length; i++) {
            if(ids[i] <= prev)
                return false;
        }

        return true;
    }

}
