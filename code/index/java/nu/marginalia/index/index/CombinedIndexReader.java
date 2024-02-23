package nu.marginalia.index.index;

import nu.marginalia.index.model.IndexQueryParams;
import nu.marginalia.index.ReverseIndexReader;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.query.*;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A reader for the combined forward and reverse indexes */
public class CombinedIndexReader {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ForwardIndexReader forwardIndexReader;
    private final ReverseIndexReader reverseIndexFullReader;
    private final ReverseIndexReader reverseIndexPriorityReader;

    public CombinedIndexReader(ForwardIndexReader forwardIndexReader,
                               ReverseIndexReader reverseIndexFullReader,
                               ReverseIndexReader reverseIndexPriorityReader) {
        this.forwardIndexReader = forwardIndexReader;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPriorityReader = reverseIndexPriorityReader;
    }


    /** Creates a query builder for terms in the priority index */
    public IndexQueryBuilder findPriorityWord(IndexQueryPriority priority, long wordId, int fetchSizeMultiplier) {
        return new IndexQueryBuilderImpl(reverseIndexFullReader, reverseIndexPriorityReader,
                new IndexQuery(
                        List.of(reverseIndexPriorityReader.documents(wordId)),
                        priority,
                        fetchSizeMultiplier), wordId);
    }

    /** Creates a query builder for terms in the full index */
    public IndexQueryBuilder findFullWord(IndexQueryPriority priority, long wordId, int fetchSizeMultiplier) {
        return new IndexQueryBuilderImpl(reverseIndexFullReader, reverseIndexPriorityReader,
                new IndexQuery(List.of(reverseIndexFullReader.documents(wordId)),
                        priority,
                        fetchSizeMultiplier),
                wordId);
    }

    /** Creates a parameter matching filter step for the provided parameters */
    public QueryFilterStepIf filterForParams(IndexQueryParams params) {
        return new IndexQueryBuilderImpl.ParamMatchingQueryFilter(params, forwardIndexReader);
    }

    /** Returns the number of occurrences of the word in the full index */
    public long numHits(long word) {
        return reverseIndexFullReader.numDocuments(word);
    }

    /** Returns the number of occurrences of the word in the priority index */
    public long numHitsPrio(long word) {
        return reverseIndexPriorityReader.numDocuments(word);
    }

    /** Retrieves the term metadata for the specified word for the provided documents */
    public long[] getMetadata(long wordId, long[] docIds) {
        return reverseIndexFullReader.getTermMeta(wordId, docIds);
    }

    /** Retrieves the document metadata for the specified document */
    public long getDocumentMetadata(long docId) {
        return forwardIndexReader.getDocMeta(docId);
    }

    /** Returns the total number of documents in the index */
    public int totalDocCount() {
        return forwardIndexReader.totalDocCount();
    }

    /** Retrieves the HTML features for the specified document */
    public int getHtmlFeatures(long docId) {
        return forwardIndexReader.getHtmlFeatures(docId);
    }

    /** Close the indexes (this is not done immediately)
     * */
    public void close() throws InterruptedException {
       /* Delay the invocation of close method to allow for a clean shutdown of the service.
        *
        * This is especially important when using Unsafe-based LongArrays, since we have
        * concurrent access to the underlying memory-mapped file.  If pull the rug from
        * under the caller by closing the file, we'll get a SIGSEGV.  Even with MemorySegment,
        * we'll get ugly stacktraces if we close the file while a thread is still accessing it.
        */

        delayedCall(forwardIndexReader::close, Duration.ofMinutes(1));
        delayedCall(reverseIndexFullReader::close, Duration.ofMinutes(1));
        delayedCall(reverseIndexPriorityReader::close, Duration.ofMinutes(1));
    }


    private void delayedCall(Runnable call, Duration delay) throws InterruptedException {
        Thread.ofPlatform().start(() -> {
            try {
                TimeUnit.SECONDS.sleep(delay.toSeconds());
                call.run();
            } catch (InterruptedException e) {
                logger.error("Interrupted", e);
            }
        });
    }

    /** Returns true if index data is available */
    public boolean isLoaded() {
        // We only need to check one of the readers, as they are either all loaded or none are
        return forwardIndexReader.isLoaded();
    }
}
