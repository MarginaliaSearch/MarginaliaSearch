package nu.marginalia.wmsa.edge.index.postings;

import nu.marginalia.wmsa.edge.index.EdgeIndexControl;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.query.IndexQuery;
import nu.marginalia.wmsa.edge.index.query.IndexQueryParams;
import nu.marginalia.wmsa.edge.index.query.IndexResultDomainDeduplicator;
import nu.marginalia.wmsa.edge.index.query.filter.QueryFilterStepFromPredicate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongPredicate;

public class SearchIndex {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile SearchIndexReader indexReader;

    private final ReadWriteLock indexReplacementLock = new ReentrantReadWriteLock();

    @NotNull
    private final IndexServicesFactory servicesFactory;
    private final EdgeIndexControl indexControl;

    public SearchIndex(@NotNull IndexServicesFactory servicesFactory, EdgeIndexControl indexControl) {
        this.servicesFactory = servicesFactory;
        this.indexControl = indexControl;
    }

    public void init() {
        Lock lock = indexReplacementLock.writeLock();

        try {
            lock.lock();
            logger.info("Initializing bucket");

            if (indexReader == null) {
                indexReader = servicesFactory.getSearchIndexReader();
            }
        }
        catch (Exception ex) {
            logger.error("Uncaught exception", ex);
        }
        finally {
            lock.unlock();
        }
    }

    public boolean switchIndex() throws IOException {

        indexControl.regenerateIndex();

        Lock lock = indexReplacementLock.writeLock();
        try {
            lock.lock();

            indexControl.switchIndexFiles();

            indexReader = servicesFactory.getSearchIndexReader();
        }
        catch (Exception ex) {
            logger.error("Uncaught exception", ex);
        }
        finally {
            lock.unlock();
        }

        return true;
    }


    public boolean isAvailable() {
        return indexReader != null;
    }

    public IndexQuery getQuery(EdgeIndexQuerySearchTerms terms, IndexQueryParams params, LongPredicate includePred) {

        if (null == indexReader) {
            logger.warn("Index reader not ready");
            return new IndexQuery(Collections.emptyList());
        }

        final int[] orderedIncludes = terms.sortedDistinctIncludes(this::compareKeywords);

        SearchIndexReader.IndexQueryBuilder query =
            switch(params.queryStrategy()) {
                case SENTENCE               -> indexReader.findWordAsSentence(orderedIncludes);
                case TOPIC                  -> indexReader.findWordAsTopic(orderedIncludes);
                case AUTO                   -> indexReader.findWordTopicDynamicMode(orderedIncludes);
            };

        if (query == null) {
            return new IndexQuery(Collections.emptyList());
        }

        query.addInclusionFilter(new QueryFilterStepFromPredicate(includePred));

        for (int i = 0; i < orderedIncludes.length; i++) {
            query = query.also(orderedIncludes[i]);
        }

        for (int term : terms.excludes()) {
            query = query.not(term);
        }

        // Run these last, as they'll worst-case cause as many page faults as there are
        // items in the buffer
        query.addInclusionFilter(indexReader.filterForParams(params));

        return query.build();
    }

    private int compareKeywords(int a, int b) {
        return Long.compare(
                indexReader.numHits(a),
                indexReader.numHits(b)
        );
    }


    public IndexQuery getDomainQuery(int wordId, IndexResultDomainDeduplicator localFilter) {
        throw new UnsupportedOperationException(""); // TBI
        /*
        var query = indexReader.findDomain(wordId);

        query.addInclusionFilter(new QueryFilterStepFromPredicate(localFilter::filterRawValue));

        return query;*/
    }

    /** Replaces the values of ids with their associated metadata, or 0L if absent */
    public long[] getTermMetadata(int termId, long[] docs) {
        return indexReader.getMetadata(termId, docs);
    }

    public long getDocumentMetadata(long docId) {
        return indexReader.getDocumentMetadata(docId);
    }

    public int getDomainId(long docId) {
        return indexReader.getDomainId(docId);
    }
}
