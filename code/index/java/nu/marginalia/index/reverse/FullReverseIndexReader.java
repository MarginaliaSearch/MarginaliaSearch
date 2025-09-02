package nu.marginalia.index.reverse;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.model.CombinedDocIdList;
import nu.marginalia.index.model.TermMetadataList;
import nu.marginalia.index.reverse.positions.PositionsFileReader;
import nu.marginalia.index.reverse.positions.TermData;
import nu.marginalia.index.reverse.query.*;
import nu.marginalia.index.reverse.query.filter.QueryFilterLetThrough;
import nu.marginalia.index.reverse.query.filter.QueryFilterNoPass;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListConstants;
import nu.marginalia.skiplist.SkipListReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

public class FullReverseIndexReader {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LongArray words;
    private final LongArray documents;

    private final PositionsFileReader positionsFileReader;

    private final long wordsDataOffset;
    private final BTreeReader wordsBTreeReader;

    private final BufferPool dataPool;
    private final String name;

    public FullReverseIndexReader(String name,
                                  Path words,
                                  Path documents,
                                  PositionsFileReader positionsFileReader)
            throws IOException
    {
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

        dataPool = new BufferPool(documents, SkipListConstants.BLOCK_SIZE,
                (int) (Long.getLong("index.bufferPoolSize", 512*1024*1024L) / SkipListConstants.BLOCK_SIZE)
        );

        wordsBTreeReader = new BTreeReader(this.words, ReverseIndexParameters.wordsBTreeContext, 0);
        wordsDataOffset = wordsBTreeReader.getHeader().dataOffsetLongs();
    }

    public void reset() {
        dataPool.reset();
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

    /** Get term metadata for each document, return an array of TermMetadataList of the same
     * length and order as termIds, with each list of the same length and order as docIds
     *
     * @throws TimeoutException if the read could not be queued in a timely manner;
     *                          (the read itself may still exceed the budgeted time)
     */
    public TermMetadataList[] getTermData(Arena arena,
                                          IndexSearchBudget budget,
                                          long[] termIds,
                                          CombinedDocIdList docIds)
            throws TimeoutException
    {
        // Gather all termdata to be retrieved into a single array,
        // to help cluster related disk accesses and get better I/O performance

        long[] offsetsAll = new long[termIds.length * docIds.size()];
        for (int i = 0; i < termIds.length; i++) {
            long termId = termIds[i];
            long offset = wordOffset(termId);

            if (offset < 0) {
                // This is likely a bug in the code, but we can't throw an exception here.
                logger.debug("Missing offset for word {}", termId);

                // We'll pass zero offsets to positionsFileReader.getTermData(), which will be
                // interpreted as an instruction to ignore these positions.
                continue;
            }

            // Read the size and offset of the position data
            long[] offsetsForTerm = getReader(offset).getValueOffsets(docIds.array());

            // Add to the big array of term data offsets
            System.arraycopy(offsetsForTerm, 0, offsetsAll, i * docIds.size(), docIds.size());
        }

        // Perform the read
        TermData[] termDataCombined = positionsFileReader.getTermData(arena, budget, offsetsAll);

        // Break the result data into separate arrays by termId again
        TermMetadataList[] ret = new TermMetadataList[termIds.length];
        for (int i = 0; i < termIds.length; i++) {
            ret[i] = new TermMetadataList(
                    Arrays.copyOfRange(termDataCombined, i*docIds.size(), (i+1)*docIds.size())
            );
        }

        return ret;
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
