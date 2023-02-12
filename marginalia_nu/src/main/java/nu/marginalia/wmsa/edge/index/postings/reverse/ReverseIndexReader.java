package nu.marginalia.wmsa.edge.index.postings.reverse;

import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexEntrySource;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexEntrySourceBehavior;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexRejectFilter;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexRetainFilter;
import nu.marginalia.wmsa.edge.index.query.EmptyEntrySource;
import nu.marginalia.wmsa.edge.index.query.EntrySource;
import nu.marginalia.wmsa.edge.index.query.filter.QueryFilterLetThrough;
import nu.marginalia.wmsa.edge.index.query.filter.QueryFilterNoPass;
import nu.marginalia.wmsa.edge.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ReverseIndexReader {
    private final LongArray words;
    private final LongArray documents;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ReverseIndexReader(Path words, Path documents) throws IOException {
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

    public EntrySource documents(int wordId, ReverseIndexEntrySourceBehavior behavior) {
        if (null == words) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource();
        }

        if (wordId < 0 || wordId >= words.size()) return new EmptyEntrySource();

        long offset = words.get(wordId);

        if (offset < 0) return new EmptyEntrySource();

        return new ReverseIndexEntrySource(createReaderNew(offset), behavior);
    }

    public QueryFilterStepIf also(int wordId) {
        if (wordId < 0) return new QueryFilterNoPass();

        long offset = words.get(wordId);

        if (offset < 0) return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(createReaderNew(offset));
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
        return new BTreeReader(documents, ReverseIndexParameters.bTreeContext, offset);
    }

    public long[] getTermMeta(int wordId, long[] docIds) {
        if (wordId < 0) {
            return new long[docIds.length];
        }

        long offset = words.get(wordId);
        if (offset < 0) {
            return new long[docIds.length];
        }

        Arrays.sort(docIds);

        var reader = createReaderNew(offset);
        return reader.queryData(docIds, 1);
    }

}
