package nu.marginalia.index.results.snippet;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.index.*;
import nu.marginalia.index.forward.doctext.DocTextDecoder;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.model.UnrankedSearchContext;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.sequence.CodedSequence;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

public class SnippetGenerator implements AutoCloseable {

    private static final ScratchSegmentAllocatorFactory allocatorFactory
            = new ScratchSegmentAllocatorFactory("SnippetGeneration", 1 << 20);

    private final CombinedIndexReader index;

    /** Ranking context of the query, or null on the unranked query path, which
     * has no term positions to bias the excerpt by */
    @Nullable
    private final SearchContext searchContext;

    private final ScratchIntListPool pool = new ScratchIntListPool(128);
    private final ScratchSegmentAllocator segmentAllocator = allocatorFactory.createAllocator();
    private final DocTextDecoder textDecoder = new DocTextDecoder();

    private final RankingBatchFetcher fetcher;

    private final float[] termWeights;
    private final int[] termClasses;

    public SnippetGenerator(CombinedIndexReader index, SearchContext searchContext) throws IOException {
        this.index = index;
        this.searchContext = searchContext;
        this.fetcher = RankingBatchFetcher.claim(index);

        if (searchContext != null) {
            termWeights = termWeights(searchContext);
            termClasses = searchContext.compiledQuery.variantClasses;
        }
        else {
            termWeights = null;
            termClasses = null;
        }
    }

    public SnippetGenerator(CombinedIndexReader index, UnrankedSearchContext searchContext) throws IOException {
        this.index = index;
        this.searchContext = null;
        this.fetcher = RankingBatchFetcher.claim(index);

        int nTerms = searchContext.termIdsRequireUnique.size();

        termWeights = new float[nTerms];
        termClasses = new int[nTerms];

        for (int i = 0; i < nTerms; i++) {
            termWeights[i] = 1.0f;
            termClasses[i] = i;
        }
    }
    
    public String[] generate(List<RankableDocument> docsList) {
        RankableDocument[] docs = docsList.toArray(new RankableDocument[0]);
        String[] snippets = new String[docs.length];

        if (docs.length == 0) {
            return snippets;
        }

        try {
            DecodableDocumentSpans[] codedSpans = fetchSpans(docs);
            MemorySegment[][] positionSegments = fetchPositionSegments(docs);

            for (int i = 0; i < docs.length; i++) {
                pool.reset();

                String text = index.getDocumentText(textDecoder, docs[i].combinedDocumentId);
                if (text == null) {
                    continue;
                }

                snippets[i] = generate(text, docs[i], i, codedSpans[i], positionSegments[i]);
            }
        }
        finally {
            pool.reset();
            segmentAllocator.reset();
        }

        return snippets;
    }

    @Nullable
    private String generate(String text,
                            RankableDocument doc,
                            int docIdx,
                            @Nullable DecodableDocumentSpans codedSpans,
                            @Nullable MemorySegment[] positionSegments)
    {
        // The title is displayed separately in the search results, so the snippet
        // should avoid the copy of it that leads the document text
        IntList titleRanges = null;
        if (codedSpans != null) {
            titleRanges = codedSpans.decode(pool::get).getSpan(HtmlTag.TITLE).startsEnds();
        }

        if (searchContext == null) {
            SentenceSnippetExtractor extractor = new SentenceSnippetExtractor(text, 64, titleRanges);

            // Grab the start of the document text as a fallback
            return extractor.extractDocumentBeginning();
        }

        IntList[] positions = decodePositions(doc, docIdx, positionSegments);

        int maxPos = -1;
        for (IntList termPositions : positions) {
            if (!termPositions.isEmpty()) {
                maxPos = Math.max(maxPos, termPositions.getInt(termPositions.size() - 1));
            }
        }

        if (maxPos == -1) {
            maxPos = 64;
        }

        SentenceSnippetExtractor extractor = new SentenceSnippetExtractor(text, maxPos, titleRanges);

        return extractor.extract(positions, termWeights, termClasses);
    }

    private DecodableDocumentSpans[] fetchSpans(RankableDocument[] docs) {
        DecodableDocumentSpans[] ret = new DecodableDocumentSpans[docs.length];
        for (int i = 0; i < docs.length; i++) {
            ret[i] = index.getDocumentSpans(segmentAllocator, docs[i].combinedDocumentId);
        }
        return ret;
    }
    
    private MemorySegment[][] fetchPositionSegments(RankableDocument[] docs) {
        if (searchContext == null) {
            return new MemorySegment[docs.length][];
        }

        MemorySegment[][] segments = fetcher.fetchPositionSegments(docs, segmentAllocator);
        fetcher.positionsDecoder().decodeBatch(segments);
        return segments;
    }

    private IntList[] decodePositions(RankableDocument doc,
                                      int docIdx,
                                      @Nullable MemorySegment[] positionSegments)
    {
        if (positionSegments != null) {
            return fetcher.positionsDecoder().positionsForDocument(positionSegments, docIdx, pool);
        }

        CodedSequence[] codedPositions = index.getTermPositions(segmentAllocator, doc.positionOffsets);
        IntList[] positions = new IntList[codedPositions.length];
        for (int i = 0; i < positions.length; i++) {
            if (codedPositions[i] != null) {
                positions[i] = codedPositions[i].values(pool::get);
            }
            else {
                positions[i] = IntList.of();
            }
        }
        return positions;
    }

    private static float[] termWeights(SearchContext searchContext) {
        int docCount = searchContext.termFreqDocCount();

        float[] weights = new float[searchContext.compiledQueryIds.size()];
        for (int i = 0; i < weights.length; i++) {
            if (!searchContext.regularMask.get(i))
                continue;

            int df = searchContext.fullCounts.get(i);
            weights[i] = (float) Math.log(1 + (docCount - df + 0.5) / (df + 0.5));
        }

        return weights;
    }

    @Override
    public void close() {
        try {
            fetcher.release();
        }
        finally {
            segmentAllocator.close();
            textDecoder.close();
        }
    }
}
