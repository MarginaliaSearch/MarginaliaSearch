package nu.marginalia.index;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.positions.TermData;
import nu.marginalia.index.positions.PositionsFileReader;
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
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public class FullReverseIndexReader {
    private final LongArray words;
    private final LongArray documents;
    private final long wordsDataOffset;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BTreeReader wordsBTreeReader;
    private final String name;

    private final PositionsFileReader positionsFileReader;

    public FullReverseIndexReader(String name,
                                  Path words,
                                  Path documents,
                                  PositionsFileReader positionsFileReader) throws IOException {
        this.name = name;

        this.positionsFileReader = positionsFileReader;

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

//        ReverseIndexSelfTest.runSelfTest1(wordsDataRange, wordsDataSize);
//        ReverseIndexSelfTest.runSelfTest2(wordsDataRange, documents);
//        ReverseIndexSelfTest.runSelfTest3(wordsDataRange, wordsBTreeReader);
//        ReverseIndexSelfTest.runSelfTest4(wordsDataRange, documents);
        ReverseIndexSelfTest.runSelfTest5(wordsDataRange, wordsBTreeReader);
        ReverseIndexSelfTest.runSelfTest6(wordsDataRange, documents);
    }


    /** Calculate the offset of the word in the documents.
     * If the return-value is negative, the term does not exist
     * in the index.
     */
    long wordOffset(long termId) {
        long idx = wordsBTreeReader.findEntry(termId);

        if (idx < 0)
            return -1L;

        return words.get(wordsDataOffset + idx + 1);
    }

    public EntrySource documents(long termId) {
        if (null == words) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource();
        }

        long offset = wordOffset(termId);

        if (offset < 0) // No documents
            return new EmptyEntrySource();

        return new ReverseIndexEntrySource(name, createReaderNew(offset), 2, termId);
    }

    /** Create a filter step requiring the specified termId to exist in the documents */
    public QueryFilterStepIf also(long termId) {
        long offset = wordOffset(termId);

        if (offset < 0) // No documents
            return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(createReaderNew(offset), name, termId);
    }

    /** Create a filter step requiring the specified termId to be absent from the documents */
    public QueryFilterStepIf not(long termId) {
        long offset = wordOffset(termId);

        if (offset < 0) // No documents
            return new QueryFilterLetThrough();

        return new ReverseIndexRejectFilter(createReaderNew(offset));
    }

    /** Return the number of documents with the termId in the index */
    public int numDocuments(long termId) {
        long offset = wordOffset(termId);

        if (offset < 0)
            return 0;

        return createReaderNew(offset).numEntries();
    }

    /** Create a BTreeReader for the document offset associated with a termId */
    private BTreeReader createReaderNew(long offset) {
        return new BTreeReader(
                documents,
                ReverseIndexParameters.fullDocsBTreeContext,
                offset);
    }

    public TermData[] getTermData(Arena arena,
                                  long termId,
                                  long[] docIds)
    {
        var ret = new TermData[docIds.length];

        long offset = wordOffset(termId);

        if (offset < 0) {
            // This is likely a bug in the code, but we can't throw an exception here
            logger.debug("Missing offset for word {}", termId);
            return ret;
        }

        var reader = createReaderNew(offset);

        // Read the size and offset of the position data
        var offsets = reader.queryData(docIds, 1);

        for (int i = 0; i < docIds.length; i++) {
            if (offsets[i] == 0)
                continue;
            ret[i] = positionsFileReader.getTermData(arena, offsets[i]);
        }
        return ret;
    }

    public void close() {
        if (documents != null)
            documents.close();

        if (words != null)
            words.close();

        if (positionsFileReader != null) {
            try {
                positionsFileReader.close();
            } catch (IOException e) {
                logger.error("Failed to close positions file reader", e);
            }
        }
    }

}
