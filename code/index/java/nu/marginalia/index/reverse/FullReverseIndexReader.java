package nu.marginalia.index.reverse;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.model.CombinedDocIdList;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.model.TermMetadataList;
import nu.marginalia.index.reverse.positions.PositionsFileReader;
import nu.marginalia.index.reverse.query.*;
import nu.marginalia.index.reverse.query.filter.QueryFilterLetThrough;
import nu.marginalia.index.reverse.query.filter.QueryFilterNoPass;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.sequence.CodedSequence;
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
import java.util.BitSet;
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
                                          SearchContext searchContext,
                                          CombinedDocIdList docIds)
            throws TimeoutException
    {
        // Gather all termdata to be retrieved into a single array,
        // to help cluster related disk accesses and get better I/O performance

        long[] termIds = searchContext.termIdsAll.array;

        WordLexicon lexicon = searchContext.languageContext.wordLexiconFull;
        if (null == lexicon) {
            TermMetadataList[] ret = new TermMetadataList[termIds.length];
            for (int i = 0; i < termIds.length; i++) {
                ret[i] = new TermMetadataList(new CodedSequence[docIds.size()], new byte[docIds.size()], new BitSet(docIds.size()));
            }

            return ret;
        }


        long[][] valuesForTerm = new long[termIds.length][];
        for (int i = 0; i < termIds.length; i++) {
            long termId = termIds[i];
            long offset = lexicon.wordOffset(termId);

            if (offset < 0) {
                // Likely optional term that is missing from the index
                logger.debug("Missing offset for word {}", termId);
                continue;
            }

            // Read the size and offset of the position data, as well as their metadata masks
            valuesForTerm[i] = getReader(offset).getAllValues(docIds.array());
        }

        BitSet viableDocuments = preselectViableDocuments(searchContext, docIds.size(), valuesForTerm);

        long[] offsetsAll = new long[termIds.length * docIds.size()];

        for (int i = 0; i < termIds.length; i++) {
            // Add to the big array of term data offsets
            long[] values = valuesForTerm[i];
            if (null == values)
                continue;

            for (int di = 0; di < docIds.size(); di++) {
                if (!viableDocuments.get(di))
                    continue;

                // We can omit the position data retrieval if the position mask is zero
                // (likely a synthetic keyword, n-gram, etc.)
                long positionMask = values[docIds.size() + di] & ~0xFFL;
                if (positionMask == 0)
                    continue;

                offsetsAll[i * docIds.size() + di] = values[di];
            }
        }

        // Perform the read
        CodedSequence[] termDataCombined = positionsFileReader.getTermData(arena, searchContext.budget, offsetsAll);

        // Break the result data into separate arrays by termId again
        TermMetadataList[] ret = new TermMetadataList[termIds.length];
        for (int i = 0; i < termIds.length; i++) {

            // Extract the term flags

            byte[] flags = new byte[docIds.size()];
            long[] values = valuesForTerm[i];

            if (null != values) {
                for (int di = 0; di < flags.length; di++) {
                    flags[di] = (byte) (values[di + docIds.size()] & 0xFFL);
                }
            }

            // Build the return array
            ret[i] = new TermMetadataList(
                    Arrays.copyOfRange(termDataCombined, i*docIds.size(), (i+1)*docIds.size()),
                    flags,
                    viableDocuments
            );
        }

        return ret;
    }

    /** Find all docIds with non-flagged terms adjacent in the document */
    BitSet preselectViableDocuments(SearchContext context, int nDocIds, long[][] valuesForTerm) {
        final int valueStartOffset = nDocIds;

        BitSet ret = new BitSet(nDocIds);

        short[] sparseCount = new short[nDocIds];

        for (long[] vals : valuesForTerm) {
            if (null == vals)
                continue;

            for (int i = 0; i < nDocIds; i++) {
                if ((vals[i] & 0xFF) != 0)
                    continue;

                int pc = Long.bitCount(vals[valueStartOffset + i]);
                if (pc <= 5) sparseCount[i]++;
            }
        }

        // Looks icky and O(n^3), but paths and paths[i] are typically very small
        // Algo below gives good memory access patterns

        long[] combinedMasks = new long[nDocIds];
        long[] thisMask = new long[nDocIds];

        outer:
        for (IntList path : context.compiledQueryIds.paths) {
            Arrays.fill(thisMask, ~0L);

            for (int pathIdx : path) {
                long[] values = valuesForTerm[pathIdx];

                if (null == values) continue outer; // We can skip this branch
                if (values.length != 2*nDocIds) throw new IllegalArgumentException("values.length had unexpected value");

                for (int i = 0; i < nDocIds; i++) {
                    long value = values[valueStartOffset + i];

                    // apply the mask only if it is not flagged,
                    // and the count is low, and we have sparse words to mask with,
                    // or all terms are non-sparse

                    if ((value & 0xFF) == 0 || (sparseCount[i] <= 2 || Long.bitCount(value) <= 5))
                        thisMask[i] &= value;
                }
            }

            // combine values of alternative evaluation paths
            for (int i = 0; i < nDocIds; i++) {
                combinedMasks[i] |= thisMask[i];
            }
        }

        for (int i = 0; i < combinedMasks.length; i++) {
            if (combinedMasks[i] != 0L)
                ret.set(i);
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
