package nu.marginalia.index.index;

import nu.marginalia.index.ReverseIndexReader;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.forward.ParamMatchingQueryFilter;
import nu.marginalia.index.query.*;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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
