package nu.marginalia.index.results.snippet;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.index.CombinedIndexReader;
import nu.marginalia.index.ScratchIntListPool;
import nu.marginalia.index.ScratchSegmentAllocator;
import nu.marginalia.index.ScratchSegmentAllocatorFactory;
import nu.marginalia.index.forward.doctext.DocTextDecoder;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.model.UnrankedSearchContext;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.sequence.CodedSequence;

import javax.annotation.Nullable;

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

    private final float[] termWeights;
    private final int[] termClasses;

    public SnippetGenerator(CombinedIndexReader index, SearchContext searchContext) {
        this.index = index;
        this.searchContext = searchContext;

        if (searchContext != null) {
            termWeights = termWeights(searchContext);
            termClasses = SearchContext.variantClasses(
                    searchContext.compiledQuery.root(),
                    searchContext.compiledQuery.size());
        }
        else {
            termWeights = null;
            termClasses = null;
        }
    }

    public SnippetGenerator(CombinedIndexReader index, UnrankedSearchContext searchContext) {
        this.index = index;
        this.searchContext = null;

        int nTerms = searchContext.termIdsRequireUnique.size();

        termWeights = new float[nTerms];
        termClasses = new int[nTerms];

        for (int i = 0; i < nTerms; i++) {
            termWeights[i] = 1.0f;
            termClasses[i] = i;
        }
    }

    @Nullable
    public String generate(RankableDocument doc) {
        String text = index.getDocumentText(textDecoder, doc.combinedDocumentId);
        if (text == null) {
            return null;
        }

        pool.reset();
        segmentAllocator.reset();

        // The title is displayed separately in the search results, so the snippet
        // should avoid the copy of it that leads the document text
        IntList titleRanges = null;
        DecodableDocumentSpans codedSpans = index.getDocumentSpans(segmentAllocator, doc.combinedDocumentId);
        if (codedSpans != null) {
            titleRanges = codedSpans.decode(pool::get).getSpan(HtmlTag.TITLE).startsEnds();
        }

        SentenceSnippetExtractor extractor = new SentenceSnippetExtractor(text, titleRanges);

        if (searchContext == null) {
            // Grab the start of the document text as a fallback
            return extractor.extractDocumentBeginning();
        }

        // The term position data the document held during ranking lives in recycled
        // scratch buffers at this stage, and must be re-fetched for snippet generation
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

        return extractor.extract(positions, termWeights, termClasses);
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
        segmentAllocator.close();
        textDecoder.close();
    }
}
