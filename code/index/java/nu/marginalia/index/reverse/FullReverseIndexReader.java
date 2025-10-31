package nu.marginalia.index.reverse;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.pool.BufferPool;
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class FullReverseIndexReader {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, WordLexicon> wordLexiconMap;

    private final LongArray documents;
    private final PositionsFileReader positionsFileReader;
    private final BufferPool dataPool;
    private final String name;

    public FullReverseIndexReader(String name,
                                  Collection<WordLexicon> wordLexicons,
                                  Path documents,
                                  Path positionsFile)
            throws IOException
    {
        this.name = name;

        if (!Files.exists(documents)) {
            this.documents = null;
            this.dataPool = null;
            this.positionsFileReader = null;
            this.wordLexiconMap = Map.of();

            wordLexicons.forEach(WordLexicon::close);

            return;
        }

        this.wordLexiconMap = wordLexicons.stream().collect(Collectors.toUnmodifiableMap(lexicon -> lexicon.languageIsoCode, v->v));
        this.positionsFileReader = new PositionsFileReader(positionsFile);

        logger.info("Switching reverse index");

        this.documents = LongArrayFactory.mmapForReadingShared(documents);

        LinuxSystemCalls.madviseRandom(this.documents.getMemorySegment());

        dataPool = new BufferPool(documents, SkipListConstants.BLOCK_SIZE,
                (int) (Long.getLong("index.bufferPoolSize", 512*1024*1024L) / SkipListConstants.BLOCK_SIZE)
        );

    }

    public void reset() {
        dataPool.reset();
    }


    public EntrySource documents(IndexLanguageContext languageContext, long termId) {
        if (null == languageContext.wordLexiconFull) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource();
        }

        long offset = languageContext.wordLexiconFull.wordOffset(termId);

        if (offset < 0) // No documents
            return new EmptyEntrySource();

        return new FullIndexEntrySource(name, getReader(offset), termId);
    }

    /** Create a filter step requiring the specified termId to exist in the documents */
    public QueryFilterStepIf also(IndexLanguageContext languageContext, long termId, IndexSearchBudget budget) {
        var lexicon = languageContext.wordLexiconFull;
        if (null == lexicon)
            return new QueryFilterNoPass();

        long offset = lexicon.wordOffset(termId);
        if (offset < 0) // No documents
            return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(getReader(offset), name, termId, budget);
    }

    /** Create a filter step requiring the specified termId to be absent from the documents */
    public QueryFilterStepIf not(IndexLanguageContext languageContext, long termId, IndexSearchBudget budget) {
        var lexicon = languageContext.wordLexiconFull;
        if (null == lexicon)
            return new QueryFilterLetThrough();

        long offset = lexicon.wordOffset(termId);

        if (offset < 0) // No documents
            return new QueryFilterLetThrough();

        return new ReverseIndexRejectFilter(getReader(offset), budget);
    }

    /** Return the number of documents with the termId in the index */
    public int numDocuments(IndexLanguageContext languageContext, long termId) {
        var lexicon = languageContext.wordLexiconFull;
        if (null == lexicon)
            return 0;

        long offset = lexicon.wordOffset(termId);

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
                                          IndexLanguageContext languageContext,
                                          IndexSearchBudget budget,
                                          long[] termIds,
                                          CombinedDocIdList docIds)
            throws TimeoutException
    {
        // Gather all termdata to be retrieved into a single array,
        // to help cluster related disk accesses and get better I/O performance

        WordLexicon lexicon = languageContext.wordLexiconFull;
        if (null == lexicon) {
            TermMetadataList[] ret = new TermMetadataList[termIds.length];
            for (int i = 0; i < termIds.length; i++) {
                ret[i] = new TermMetadataList(new TermData[docIds.size()]);
            }

            return ret;
        }

        long[] offsetsAll = new long[termIds.length * docIds.size()];
        for (int i = 0; i < termIds.length; i++) {
            long termId = termIds[i];
            long offset = lexicon.wordOffset(termId);

            if (offset < 0) {
                // This is likely a bug in the code, but we can't throw an exception here.
                logger.debug("Missing offset for word {}", termId);

                // We'll pass zero offsets to positionsFileReader.getTermData(), which will be
                // interpreted as an instruction to ignore these positions.
                continue;
            }

            // Read the size and offset of the position data
            long[] offsetsForTerm = getReader(offset).getValues(docIds.array(), 0);

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

        wordLexiconMap.values().forEach(WordLexicon::close);

        if (positionsFileReader != null) {
            try {
                positionsFileReader.close();
            } catch (IOException e) {
                logger.error("Failed to close positions file reader", e);
            }
        }
    }

    @Nullable
    public WordLexicon getWordLexicon(String languageIsoCode) {
        return wordLexiconMap.get(languageIsoCode);
    }
}
