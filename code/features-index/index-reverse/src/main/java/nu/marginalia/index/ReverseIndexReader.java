package nu.marginalia.index;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EmptyEntrySource;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.index.query.ReverseIndexRejectFilter;
import nu.marginalia.index.query.ReverseIndexRetainFilter;
import nu.marginalia.index.query.filter.QueryFilterLetThrough;
import nu.marginalia.index.query.filter.QueryFilterNoPass;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public class ReverseIndexReader {
    private final LongArray words;
    private final LongArray documents;
    private final long wordsDataOffset;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BTreeReader wordsBTreeReader;

    public ReverseIndexReader(Path words, Path documents) throws IOException {
        if (!Files.exists(words) || !Files.exists(documents)) {
            this.words = null;
            this.documents = null;
            this.wordsBTreeReader = null;
            this.wordsDataOffset = -1;
            return;
        }

        logger.info("Switching reverse index");

        this.words = LongArrayFactory.mmapForReadingShared(words);
        this.documents = LongArrayFactory.mmapForReadingShared(documents);

        wordsBTreeReader = new BTreeReader(this.words, ReverseIndexParameters.wordsBTreeContext, 0);
        wordsDataOffset = wordsBTreeReader.getHeader().dataOffsetLongs();

        if (getClass().desiredAssertionStatus()) {
            if (Boolean.getBoolean("index-self-test")) {
                Executors.newSingleThreadExecutor().execute(this::selfTest);
            }
        }
    }

    private void selfTest() {
        logger.info("Running self test program");

        long wordsDataSize = wordsBTreeReader.getHeader().numEntries() * 2L;

        var wordsDataRange = words.range(wordsDataOffset, wordsDataOffset + wordsDataSize);
        if (!wordsDataRange.isSortedN(2, 0, wordsDataSize))
            logger.error("Failed test 1: Words data is not sorted");
        else
            logger.info("Passed test 1");

        boolean failed2 = false;
        for (long i = 1; i < wordsDataRange.size(); i+=2) {
            var docsBTreeReader = new BTreeReader(this.documents, ReverseIndexParameters.docsBTreeContext, wordsDataRange.get(i));
            var header = docsBTreeReader.getHeader();
            var docRange = documents.range(header.dataOffsetLongs(), header.dataOffsetLongs() + header.numEntries() * 2L);
            if (!docRange.isSortedN(2, 0, header.numEntries() * 2L)) {
                logger.error("Failed test 2: numEntries={}, offset={}", header.numEntries(), header.dataOffsetLongs());
                failed2 = true;
                break;
            }
        }
        if (!failed2)
            logger.info("Passed test 2");

        boolean failed3 = false;
        for (long i = 0; i < wordsDataRange.size(); i+=2) {
            if (wordOffset(wordsDataRange.get(i)) < 0) {
                failed3 = true;

                logger.error("Failed test 3");
                if (wordsBTreeReader.findEntry(wordsDataRange.get(i)) < 0) {
                    logger.error("Scenario A");
                }
                else {
                    logger.error("Scenario B");
                }

                break;
            }
        }
        if (!failed3) {
            logger.info("Passed test 3");
        }

        boolean failed4 = false;
        outer:
        for (long i = 1; i < wordsDataRange.size(); i+=2) {
            var docsBTreeReader = new BTreeReader(this.documents, ReverseIndexParameters.docsBTreeContext, wordsDataRange.get(i));
            var header = docsBTreeReader.getHeader();
            var docRange = documents.range(header.dataOffsetLongs(), header.dataOffsetLongs() + header.numEntries() * 2L);
            for (int j = 0; j < docRange.size(); j+=2) {
                if (docsBTreeReader.findEntry(docRange.get(j)) < 0) {
                    logger.info("Failed test 4");
                    break outer;
                }
            }
        }
        if (!failed4) {
            logger.info("Passed test 4");
        }
    }


    private long wordOffset(long wordId) {
        long idx = wordsBTreeReader.findEntry(wordId);

        if (idx < 0)
            return -1L;

        return words.get(wordsDataOffset + idx + 1);
    }

    public EntrySource documents(long wordId) {
        if (null == words) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource();
        }

        long offset = wordOffset(wordId);

        if (offset < 0) return new EmptyEntrySource();

        return new ReverseIndexEntrySource(createReaderNew(offset), 2, wordId);
    }

    public QueryFilterStepIf also(long wordId) {
        long offset = wordOffset(wordId);

        if (offset < 0) return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(createReaderNew(offset), "full", wordId);
    }

    public QueryFilterStepIf not(long wordId) {
        long offset = wordOffset(wordId);

        if (offset < 0) return new QueryFilterLetThrough();

        return new ReverseIndexRejectFilter(createReaderNew(offset));
    }

    public int numDocuments(long wordId) {
        long offset = wordOffset(wordId);

        if (offset < 0)
            return 0;

        return createReaderNew(offset).numEntries();
    }

    private BTreeReader createReaderNew(long offset) {
        return new BTreeReader(documents, ReverseIndexParameters.docsBTreeContext, offset);
    }

    public long[] getTermMeta(long wordId, long[] docIds) {
        long offset = wordOffset(wordId);

        if (offset < 0) {
            logger.warn("Missing offset for word {}", wordId);
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

    public void close() {
        if (documents != null)
            documents.close();

        if (words != null)
            words.close();
    }
}
