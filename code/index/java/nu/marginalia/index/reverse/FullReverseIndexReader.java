package nu.marginalia.index.reverse;

import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.model.*;
import nu.marginalia.index.reverse.positions.PositionCodec;
import nu.marginalia.index.reverse.query.*;
import nu.marginalia.index.reverse.query.filter.QueryFilterLetThrough;
import nu.marginalia.index.reverse.query.filter.QueryFilterNoPass;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.VarintCodedSequence;
import nu.marginalia.skiplist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class FullReverseIndexReader {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, WordLexicon> wordLexiconMap;

    private final LongArray documents;
    private final int positionsFileFd;
    private final BufferPool dataPool;
    private final SkipListValueReader valueReader;
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
            this.valueReader = null;
            this.positionsFileFd = -1;
            this.wordLexiconMap = Map.of();

            wordLexicons.forEach(WordLexicon::close);

            return;
        }

        this.wordLexiconMap = wordLexicons.stream().collect(Collectors.toUnmodifiableMap(lexicon -> lexicon.languageIsoCode, v->v));
        this.positionsFileFd = LinuxSystemCalls.openBuffered(positionsFile);

        logger.info("Switching reverse index");

        this.documents = LongArrayFactory.mmapForReadingShared(documents);

        LinuxSystemCalls.madviseRandom(this.documents.getMemorySegment());

        valueReader = new SkipListValueReader(documentValues);

        dataPool = new BufferPool(documents, SkipListConstants.BLOCK_SIZE,
                (int) (Long.getLong("index.bufferPoolSize", 512*1024*1024L) / SkipListConstants.BLOCK_SIZE)
        );

    }

    public boolean isLoaded() {
        return this.valueReader != null;
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
        return new SkipListReader(dataPool, valueReader, offset);
    }

    @Nullable
    @CheckReturnValue
    public SkipListReader.ValueReader getValueReader(SearchContext searchContext,
                                                               long termId,
                                                               CombinedDocIdList keys) {
        WordLexicon lexicon = searchContext.languageContext.wordLexiconFull;
        if (null == lexicon) {
            return null;
        }

        long offset = lexicon.wordOffset(termId);
        if (offset < 0)
            return null;

        return getReader(offset).getValueReader(keys.array());
    }

    public BitSet getValuePresence(SearchContext searchContext, long termId, CombinedDocIdList keys) {
        WordLexicon lexicon = searchContext.languageContext.wordLexiconFull;
        if (null == lexicon) return new BitSet(keys.size());

        long offset = lexicon.wordOffset(termId);
        if (offset < 0) return new BitSet(keys.size());

        return getReader(offset).getAllPresentValues(keys.array());
    }

    public void close() {
        try {
            if(dataPool != null)
                dataPool.close();
        }
        catch (Exception e) {
            logger.warn("Error while closing documents bufferPool", e);
        }

        if(valueReader != null)
            valueReader.close();

        if (documents != null)
            documents.close();

        wordLexiconMap.values().forEach(WordLexicon::close);

        if (positionsFileFd > 0) {
            LinuxSystemCalls.closeFd(positionsFileFd);
        }
    }

    @Nullable
    public WordLexicon getWordLexicon(String languageIsoCode) {
        return wordLexiconMap.get(languageIsoCode);
    }

    public CodedSequence[] getTermPositions(Arena arena, long[] offsets) {
        MemorySegment[] segments = new MemorySegment[offsets.length];

        for (int i = 0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;

            int size = PositionCodec.decodeSize(encodedOffset);
            long offest = PositionCodec.decodeOffset(encodedOffset);

            var segment = arena.allocate(size, 8);
            segments[i] = segment;

            LinuxSystemCalls.readAt(positionsFileFd, segment, offest);
        }

        CodedSequence[] ret = new CodedSequence[segments.length];
        for (int i = 0; i < segments.length; i++) {
            if (segments[i] != null) {
                ByteBuffer buffer = segments[i].asByteBuffer();
                ret[i] = new VarintCodedSequence(buffer, 0, buffer.capacity());
            }
        }
        return ret;
    }
}
