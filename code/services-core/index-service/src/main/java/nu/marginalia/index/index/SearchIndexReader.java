package nu.marginalia.index.index;

import nu.marginalia.index.ReverseIndexReader;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.forward.ParamMatchingQueryFilter;
import nu.marginalia.index.query.*;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SearchIndexReader {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ForwardIndexReader forwardIndexReader;
    private final ReverseIndexReader reverseIndexFullReader;
    private final ReverseIndexReader reverseIndexPriorityReader;

    public SearchIndexReader(ForwardIndexReader forwardIndexReader,
                             ReverseIndexReader reverseIndexFullReader,
                             ReverseIndexReader reverseIndexPriorityReader) {
        this.forwardIndexReader = forwardIndexReader;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPriorityReader = reverseIndexPriorityReader;
    }

    public IndexQueryBuilder findPriorityWord(IndexQueryPriority priority, long wordId, int fetchSizeMultiplier) {
        var sources = List.of(reverseIndexPriorityReader.documents(wordId));

        return new SearchIndexQueryBuilder(reverseIndexFullReader, reverseIndexPriorityReader,
                new IndexQuery(sources, priority, fetchSizeMultiplier), wordId);
    }

    public IndexQueryBuilder findFullWord(IndexQueryPriority priority, long wordId, int fetchSizeMultiplier) {
        var sources = List.of(reverseIndexFullReader.documents(wordId));

        return new SearchIndexQueryBuilder(reverseIndexFullReader, reverseIndexPriorityReader,
                new IndexQuery(sources, priority, fetchSizeMultiplier), wordId);
    }

    public QueryFilterStepIf filterForParams(IndexQueryParams params) {
        return new ParamMatchingQueryFilter(params, forwardIndexReader);
    }

    public long numHits(long word) {
        return reverseIndexFullReader.numDocuments(word);
    }
    public long numHitsPrio(long word) {
        return reverseIndexPriorityReader.numDocuments(word);
    }

    public long[] getMetadata(long wordId, long[] docIds) {
        return reverseIndexFullReader.getTermMeta(wordId, docIds);
    }

    public long getDocumentMetadata(long docId) {
        return forwardIndexReader.getDocMeta(docId);
    }

    public int totalDocCount() {
        return forwardIndexReader.totalDocCount();
    }

    public int getHtmlFeatures(long docId) {
        return forwardIndexReader.getHtmlFeatures(docId);
    }

    public void close() {
        forwardIndexReader.close();
        reverseIndexFullReader.close();
        reverseIndexPriorityReader.close();
    }
}
