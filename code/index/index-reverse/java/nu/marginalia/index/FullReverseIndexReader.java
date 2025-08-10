package nu.marginalia.index;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.positions.PositionsFileReader;
import nu.marginalia.index.positions.TermData;
import nu.marginalia.index.query.*;
import nu.marginalia.index.query.filter.QueryFilterLetThrough;
import nu.marginalia.index.query.filter.QueryFilterNoPass;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListConstants;
import nu.marginalia.skiplist.SkipListReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FullReverseIndexReader {
    private final LongArray words;
    private final LongArray documents;

    private final long wordsDataOffset;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BTreeReader wordsBTreeReader;
    private final String name;

    private final PositionsFileReader positionsFileReader;

    private final BufferPool dataPool;

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
            this.dataPool = null;
            return;
        }

        logger.info("Switching reverse index");

        this.words = LongArrayFactory.mmapForReadingShared(words);
        this.documents = LongArrayFactory.mmapForReadingShared(documents);

        LinuxSystemCalls.madviseRandom(this.words.getMemorySegment());
        LinuxSystemCalls.madviseRandom(this.documents.getMemorySegment());

        dataPool = new BufferPool(documents, SkipListConstants.BLOCK_SIZE, (int) (Long.getLong("index.bufferPoolSize", 512*1024*1024L) / SkipListConstants.BLOCK_SIZE));

        wordsBTreeReader = new BTreeReader(this.words, ReverseIndexParameters.wordsBTreeContext, 0);
        wordsDataOffset = wordsBTreeReader.getHeader().dataOffsetLongs();

        if (getClass().desiredAssertionStatus()) {
            if (Boolean.getBoolean("index-self-test")) {
                Executors.newSingleThreadExecutor().execute(this::selfTest);
            }
        }
    }

    public void reset() {
        dataPool.reset();
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

    public void eachDocRange(Consumer<LongArray> eachDocRange) {
        long wordsDataSize = wordsBTreeReader.getHeader().numEntries() * 2L;
        var wordsDataRange = words.range(wordsDataOffset, wordsDataOffset + wordsDataSize);

        for (long i = 1; i < wordsDataRange.size(); i+=2) {
            var docsBTreeReader = new BTreeReader(documents, ReverseIndexParameters.fullDocsBTreeContext, wordsDataRange.get(i));
            eachDocRange.accept(docsBTreeReader.data());
        }
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

        return new FullIndexEntrySource(name, getReader(offset), termId);
    }

    /** Create a filter step requiring the specified termId to exist in the documents */
    public QueryFilterStepIf also(long termId, IndexSearchBudget budget) {
        long offset = wordOffset(termId);

        if (offset < 0) // No documents
            return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(getReader(offset), name, termId, budget);
    }

    /** Create a filter step requiring the specified termId to be absent from the documents */
    public QueryFilterStepIf not(long termId, IndexSearchBudget budget) {
        long offset = wordOffset(termId);

        if (offset < 0) // No documents
            return new QueryFilterLetThrough();

        return new ReverseIndexRejectFilter(getReader(offset), budget);
    }

    /** Return the number of documents with the termId in the index */
    public int numDocuments(long termId) {
        long offset = wordOffset(termId);

        if (offset < 0)
            return 0;

        return getReader(offset).estimateSize();
    }

    /** Create a BTreeReader for the document offset associated with a termId */
    private SkipListReader getReader(long offset) {
        return new SkipListReader(dataPool, offset);
    }

    public TermData[] getTermData(Arena arena,
                                  long[] termIds,
                                  long[] docIds)
    {

        long[] offsetsAll = new long[termIds.length * docIds.length];

        for (int i = 0; i < termIds.length; i++) {
            long termId = termIds[i];
            long offset = wordOffset(termId);

            if (offset < 0) {
                // This is likely a bug in the code, but we can't throw an exception here
                logger.debug("Missing offset for word {}", termId);
                continue;
            }

            var reader = getReader(offset);

            // Read the size and offset of the position data
            var offsetsForTerm = reader.getValueOffsets(docIds);
            System.arraycopy(offsetsForTerm, 0, offsetsAll, i * docIds.length, docIds.length);
        }

        return positionsFileReader.getTermData(arena, offsetsAll);
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

        var reader = getReader(offset);

        // Read the size and offset of the position data
        var offsets = reader.getValueOffsets(docIds);

        return positionsFileReader.getTermData(arena, offsets);
    }

    public void close() {
        try {
            dataPool.close();
        }
        catch (Exception e) {
            logger.warn("Error while closing bufferPool", e);
        }

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
