package nu.marginalia.index.index;

import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.forward.ParamMatchingQueryFilter;
import nu.marginalia.index.query.*;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.priority.ReverseIndexPriorityReader;
import nu.marginalia.index.full.ReverseIndexFullReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SearchIndexReader {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ForwardIndexReader forwardIndexReader;
    private final ReverseIndexFullReader reverseIndexFullReader;
    private final ReverseIndexPriorityReader reverseIndexPriorityReader;

    public SearchIndexReader(ForwardIndexReader forwardIndexReader,
                             ReverseIndexFullReader reverseIndexFullReader,
                             ReverseIndexPriorityReader reverseIndexPriorityReader) {
        this.forwardIndexReader = forwardIndexReader;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPriorityReader = reverseIndexPriorityReader;
    }

    public IndexQueryBuilder findPriorityWord(IndexQueryPriority priority, int wordId, int fetchSizeMultiplier) {
        var sources = List.of(reverseIndexPriorityReader.priorityDocuments(wordId));

        return new SearchIndexQueryBuilder(reverseIndexFullReader, reverseIndexPriorityReader,
                new IndexQuery(sources, priority, fetchSizeMultiplier), wordId);
    }

    public IndexQueryBuilder findFullWord(IndexQueryPriority priority, int wordId, int fetchSizeMultiplier) {
        var sources = List.of(reverseIndexFullReader.documents(wordId));

        return new SearchIndexQueryBuilder(reverseIndexFullReader, reverseIndexPriorityReader,
                new IndexQuery(sources, priority, fetchSizeMultiplier), wordId);
    }

    public QueryFilterStepIf filterForParams(IndexQueryParams params) {
        return new ParamMatchingQueryFilter(params, forwardIndexReader);
    }

    public long numHits(int word) {
        return reverseIndexFullReader.numDocuments(word);
    }
    public long numHitsPrio(int word) {
        return reverseIndexPriorityReader.numDocuments(word);
    }

    public long[] getMetadata(int wordId, long[] docIds) {
        return reverseIndexFullReader.getTermMeta(wordId, docIds);
    }

    public long getDocumentMetadata(long docId) {
        return forwardIndexReader.getDocMeta(docId);
    }

    public int getDomainId(long docId) {
        return forwardIndexReader.getDomainId(docId);
    }

    public int totalDocCount() {
        return forwardIndexReader.totalDocCount();
    }

    public int getHtmlFeatures(long docId) {
        return forwardIndexReader.getHtmlFeatures(docId);
    }
}
