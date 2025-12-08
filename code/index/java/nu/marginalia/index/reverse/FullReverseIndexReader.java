package nu.marginalia.index.reverse;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.model.CombinedTermMetadata;
import nu.marginalia.index.model.CombinedDocIdList;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.reverse.positions.PositionsFileReader;
import nu.marginalia.index.reverse.query.*;
import nu.marginalia.index.reverse.query.filter.QueryFilterLetThrough;
import nu.marginalia.index.reverse.query.filter.QueryFilterNoPass;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.skiplist.SkipListConstants;
import nu.marginalia.skiplist.SkipListReader;
import nu.marginalia.skiplist.SkipListValueRanges;
import nu.marginalia.skiplist.SkipListWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class FullReverseIndexReader {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, WordLexicon> wordLexiconMap;

    private final LongArray documents;
    private final PositionsFileReader positionsFileReader;
    private final BufferPool dataPool;
    private final BufferPool valuesPool;
    private final String name;

    public FullReverseIndexReader(String name,
                                  Collection<WordLexicon> wordLexicons,
                                  Path documents,
                                  Path documentValues,
                                  Path positionsFile)
            throws IOException
    {
        this.name = name;

        if (!Files.exists(documents) || !Files.exists(documentValues) || !validateDocumentsFooter(documents)) {
            this.documents = null;
            this.dataPool = null;
            this.valuesPool = null;
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
        valuesPool = new BufferPool(documentValues, SkipListConstants.VALUE_BLOCK_SIZE,
                (int) (Long.getLong("index.bufferValuePoolSize", 4*1024*1024L) / SkipListConstants.VALUE_BLOCK_SIZE)
        );
    }

    private boolean validateDocumentsFooter(Path documents) {
        try {
            SkipListWriter.validateFooter(documents, "skplist-docs-file");
            return true;
        }
        catch (IllegalArgumentException|IOException ex) {
            logger.error("Failed to validate documents file footer", ex);
            return false;
        }
    }

    public void reset() {
        try {
            dataPool.reset();
            valuesPool.reset();
        }
        catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }


    public EntrySource documents(IndexLanguageContext languageContext, String term, long termId) {
        if (null == languageContext.wordLexiconFull) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource("full", term);
        }

        long offset = languageContext.wordLexiconFull.wordOffset(termId);

        if (offset < 0) // No documents
            return new EmptyEntrySource("full", term);

        return new FullIndexEntrySource(name, term, getReader(offset));
    }

    public EntrySource documents(IndexLanguageContext languageContext, SkipListValueRanges ranges, String term, long termId) {
        if (null == languageContext.wordLexiconFull) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource("full", term);
        }

        long offset = languageContext.wordLexiconFull.wordOffset(termId);

        if (offset < 0) // No documents
            return new EmptyEntrySource("full", term);

        return new FullIndexEntrySourceWithRangeFilter(name, term, getReader(offset), ranges);
    }

    /** Create a filter step requiring the specified termId to exist in the documents */
    public QueryFilterStepIf also(IndexLanguageContext languageContext, String term, long termId, IndexSearchBudget budget) {
        var lexicon = languageContext.wordLexiconFull;
        if (null == lexicon)
            return new QueryFilterNoPass();

        long offset = lexicon.wordOffset(termId);
        if (offset < 0) // No documents
            return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(getReader(offset), name, term, budget);
    }

    /** Create a filter step requiring the specified termId to exist in the documents */
    public QueryFilterStepIf any(IndexLanguageContext languageContext, List<String> terms, LongList termIds, IndexSearchBudget budget) {
        var lexicon = languageContext.wordLexiconFull;
        if (null == lexicon)
            return new QueryFilterNoPass();

        List<SkipListReader> ranges = new ArrayList<>(terms.size());
        List<String> actualTerms = new ArrayList<>(terms.size());

        for (int i = 0; i < termIds.size(); i++) {
            long termId = termIds.getLong(i);
            long offset = lexicon.wordOffset(termId);
            if (offset < 0) // No documents
                continue;
            ranges.add(getReader(offset));
            actualTerms.add(terms.get(i));
        }

        if (ranges.isEmpty()) {
            return new QueryFilterNoPass();
        }

        return new ReverseIndexMultiTermRetainFilter(ranges, name, actualTerms, budget);
    }

    /** Create a filter step requiring the specified termId to be absent from the documents */
    public QueryFilterStepIf not(IndexLanguageContext languageContext, String term, long termId, IndexSearchBudget budget) {
        var lexicon = languageContext.wordLexiconFull;
        if (null == lexicon)
            return new QueryFilterLetThrough();

        long offset = lexicon.wordOffset(termId);

        if (offset < 0) // No documents
            return new QueryFilterLetThrough();

        return new ReverseIndexRejectFilter(getReader(offset), term, budget);
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
        return new SkipListReader(dataPool, valuesPool, offset);
    }

    /** Get term metadata for each document, return an array of TermMetadataList of the same
     * length and order as termIds, with each list of the same length and order as docIds
     *
     * @throws TimeoutException if the read could not be queued in a timely manner;
     *                          (the read itself may still exceed the budgeted time)
     */
    public CombinedTermMetadata getTermData(Arena arena,
                                            SearchContext searchContext,
                                            CombinedDocIdList docIds)
            throws TimeoutException
    {
        // Gather all termdata to be retrieved into a single array,
        // to help cluster related disk accesses and get better I/O performance

        long[] termIds = searchContext.termIdsAll.array;

        WordLexicon lexicon = searchContext.languageContext.wordLexiconFull;
        if (null == lexicon) {
            CombinedTermMetadata.TermMetadataList[] ret = new CombinedTermMetadata.TermMetadataList[termIds.length];
            for (int i = 0; i < termIds.length; i++) {
                ret[i] = new CombinedTermMetadata.TermMetadataList(new CodedSequence[docIds.size()], new byte[docIds.size()]);
            }

            return new CombinedTermMetadata(ret, new BitSet[searchContext.termIdsPriority.size()], new BitSet(docIds.size()));
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

        BitSet[] priorityTermsPresent = new BitSet[searchContext.termIdsPriority.size()];

        for (int i = 0; i < searchContext.termIdsPriority.size(); i++) {
            long termId = searchContext.termIdsPriority.getLong(i);
            long offset = lexicon.wordOffset(termId);

            if (offset < 0) {
                priorityTermsPresent[i] = new BitSet();
            }
            else {
                priorityTermsPresent[i] = getReader(offset).getAllPresentValues(docIds.array());
            }
        }

        // Perform the read
        CodedSequence[] termDataCombined = positionsFileReader.getTermData(arena, searchContext.budget, offsetsAll);

        // Break the result data into separate arrays by termId again
        CombinedTermMetadata.TermMetadataList[] ret = new CombinedTermMetadata.TermMetadataList[termIds.length];
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
            ret[i] = new CombinedTermMetadata.TermMetadataList(
                    Arrays.copyOfRange(termDataCombined, i*docIds.size(), (i+1)*docIds.size()),
                    flags
            );
        }

        return new CombinedTermMetadata(ret, priorityTermsPresent, viableDocuments);
    }

    /** Find all docIds with non-flagged terms adjacent in the document */
    BitSet preselectViableDocuments(SearchContext context, int nDocIds, long[][] valuesForTerm) {

        BitSet ret = new BitSet(nDocIds);

        // Operate in slices of 16 documents.  This should line up with 2 cache lines for L1 for the long arrays,
        // and reduces the allocation overhead significantly for expected nDocIds values.

        final int sliceStep = 16;


        long[] combinedMasks = new long[sliceStep];
        long[] thisMask = new long[sliceStep];

        int[] bestFlagsCount = new int[sliceStep];
        int[] minFlagCount = new int[sliceStep];

        for (int sliceStart = 0; sliceStart < nDocIds; sliceStart += sliceStep) {
            int sliceEnd = Math.min(sliceStart + sliceStep, nDocIds);
            int sliceSize = sliceEnd - sliceStart;

            final int valueStartOffset = nDocIds + sliceStart;

            outer:
            for (IntList path : context.compiledQueryIds.paths) {
                Arrays.fill(thisMask, ~0L);
                Arrays.fill(minFlagCount, Integer.MAX_VALUE);

                for (int pathIdx : path) {
                    long[] values = valuesForTerm[pathIdx];

                    if (null == values) continue outer; // We can skip this branch
                    if (values.length != 2 * nDocIds)
                        throw new IllegalArgumentException("values.length had unexpected value");

                    for (int i = 0; i < sliceSize; i++) {
                        long value = values[valueStartOffset + i];

                        minFlagCount[i] = Math.min(minFlagCount[i], Long.bitCount((value & 0xFF)));

                        if (WordFlags.Synthetic.isPresent((byte) value))
                            continue;

                        if ((value & 0xFF) == 0)
                            thisMask[i] &= value;
                    }
                }

                // combine values of alternative evaluation paths
                for (int i = 0; i < sliceSize; i++) {
                    combinedMasks[i] |= thisMask[i];
                }
                for (int i = 0; i < sliceSize; i++) {
                    bestFlagsCount[i] = Math.max(minFlagCount[i], bestFlagsCount[i]);
                }
            }

            for (int i = 0; i < sliceSize; i++) {
                if (combinedMasks[i] != 0L || minFlagCount[i] > 0)
                    ret.set(sliceStart + i);
            }
        }

        return ret;
    }

    public void close() {
        try {
            if(dataPool != null)
                dataPool.close();
        }
        catch (Exception e) {
            logger.warn("Error while closing documents bufferPool", e);
        }

        try {
            if(valuesPool != null)
                valuesPool.close();
        }
        catch (Exception e) {
            logger.warn("Error while closing values bufferPool", e);
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
