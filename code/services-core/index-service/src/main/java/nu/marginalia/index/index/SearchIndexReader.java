package nu.marginalia.index.index;

import nu.marginalia.index.ReverseIndexReader;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.forward.ParamMatchingQueryFilter;
import nu.marginalia.index.query.*;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

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

    public void close() throws InterruptedException {
        tryClose(forwardIndexReader::close);
        tryClose(reverseIndexFullReader::close);
        tryClose(reverseIndexPriorityReader::close);
    }

    /* Try to close the given resource, retrying a few times if it fails.
     *  There is a small but non-zero chance we're closing during a query,
     *  which will cause an IllegalStateException to be thrown.  We don't
     *  want to add synchronization to the query code, so we just retry a
     *  few times, fingers crossed.  If worse comes to worst, and we leak resources,
     *  the GC will clean this up eventually...
     * */
    private void tryClose(Runnable closeMethod) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            try {
                closeMethod.run();
                return;
            } catch (Exception ex) {
                logger.error("Error closing", ex);
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        logger.error("Failed to close index");
    }

    /** Returns true if index data is available */
    public boolean isLoaded() {
        // We only need to check one of the readers, as they are either all loaded or none are
        return forwardIndexReader.isLoaded();
    }
}
