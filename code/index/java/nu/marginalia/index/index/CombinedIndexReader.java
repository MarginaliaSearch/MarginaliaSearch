package nu.marginalia.index.index;

import nu.marginalia.index.ReverseIndexReader;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.DocMetadataList;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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

    public IndexQueryBuilderImpl newQueryBuilder(IndexQuery query) {
        return new IndexQueryBuilderImpl(reverseIndexFullReader, reverseIndexPriorityReader, query);
    }

    public QueryFilterStepIf hasWordFull(long termId) {
        return reverseIndexFullReader.also(termId);
    }

    public QueryFilterStepIf hasWordPrio(long termId) {
        return reverseIndexPriorityReader.also(termId);
    }


    /** Creates a query builder for terms in the priority index */
    public IndexQueryBuilder findPriorityWord(long wordId) {
        return newQueryBuilder(new IndexQuery(reverseIndexPriorityReader.documents(wordId)))
                .withSourceTerms(wordId);
    }

    /** Creates a query builder for terms in the full index */
    public IndexQueryBuilder findFullWord(long wordId) {
        return newQueryBuilder(
                new IndexQuery(reverseIndexFullReader.documents(wordId)))
                .withSourceTerms(wordId);
    }

    /** Creates a parameter matching filter step for the provided parameters */
    public QueryFilterStepIf filterForParams(QueryParams params) {
        return new ParamMatchingQueryFilter(params, forwardIndexReader);
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
    public DocMetadataList getMetadata(long wordId, CombinedDocIdList docIds) {
        return new DocMetadataList(reverseIndexFullReader.getTermMeta(wordId, docIds.array()));
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

class ParamMatchingQueryFilter implements QueryFilterStepIf {
    private final QueryParams params;
    private final ForwardIndexReader forwardIndexReader;

    public ParamMatchingQueryFilter(QueryParams params,
                                    ForwardIndexReader forwardIndexReader)
    {
        this.params = params;
        this.forwardIndexReader = forwardIndexReader;
    }

    @Override
    public boolean test(long combinedId) {
        long docId = UrlIdCodec.removeRank(combinedId);
        int domainId = UrlIdCodec.getDomainId(docId);

        long meta = forwardIndexReader.getDocMeta(docId);

        if (!validateDomain(domainId, meta)) {
            return false;
        }

        if (!validateQuality(meta)) {
            return false;
        }

        if (!validateYear(meta)) {
            return false;
        }

        if (!validateSize(meta)) {
            return false;
        }

        if (!validateRank(meta)) {
            return false;
        }

        return true;
    }

    private boolean validateDomain(int domainId, long meta) {
        return params.searchSet().contains(domainId, meta);
    }

    private boolean validateQuality(long meta) {
        final var limit = params.qualityLimit();

        if (limit.type() == SpecificationLimitType.NONE) {
            return true;
        }

        final int quality = DocumentMetadata.decodeQuality(meta);

        return limit.test(quality);
    }

    private boolean validateYear(long meta) {
        if (params.year().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeYear(meta);

        return params.year().test(postVal);
    }

    private boolean validateSize(long meta) {
        if (params.size().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeSize(meta);

        return params.size().test(postVal);
    }

    private boolean validateRank(long meta) {
        if (params.rank().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeRank(meta);

        return params.rank().test(postVal);
    }

    @Override
    public double cost() {
        return 32;
    }

    @Override
    public String describe() {
        return getClass().getSimpleName();
    }
}