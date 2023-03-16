package nu.marginalia.index.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.results.IndexResultDomainDeduplicator;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.index.query.filter.QueryFilterStepFromPredicate;
import nu.marginalia.index.svc.IndexSearchSetsService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongPredicate;

/** This class delegates SearchIndexReader and deals with the stateful nature of the index,
 * i.e. it may be possible to reconstruct the index and load a new set of data.
 *
 */
@Singleton
public class SearchIndex {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile SearchIndexReader indexReader;

    private final ReadWriteLock indexReplacementLock = new ReentrantReadWriteLock();

    @NotNull
    private final IndexServicesFactory servicesFactory;
    private final IndexSearchSetsService searchSetsService;

    @Inject
    public SearchIndex(@NotNull IndexServicesFactory servicesFactory, IndexSearchSetsService searchSetsService) {
        this.servicesFactory = servicesFactory;
        this.searchSetsService = searchSetsService;
    }

    public void init() {
        Lock lock = indexReplacementLock.writeLock();

        try {
            lock.lock();
            logger.info("Initializing index");

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

        servicesFactory.convertIndex(searchSetsService.getDomainRankings());
        System.gc();

        Lock lock = indexReplacementLock.writeLock();
        try {
            lock.lock();

            servicesFactory.switchFilesJob().call();

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

    public IndexQuery createQuery(SearchIndexSearchTerms terms, IndexQueryParams params, LongPredicate includePred) {

        if (null == indexReader) {
            logger.warn("Index reader not ready");
            return new IndexQuery(Collections.emptyList());
        }

        final int[] orderedIncludes = terms.sortedDistinctIncludes(this::compareKeywords);

        IndexQueryBuilder query =
            switch(params.queryStrategy()) {
                case SENTENCE               -> indexReader.findWordAsSentence(orderedIncludes);
                case TOPIC, REQUIRE_FIELD_SITE, REQUIRE_FIELD_TITLE, REQUIRE_FIELD_SUBJECT, REQUIRE_FIELD_DOMAIN, REQUIRE_FIELD_URL
                                            -> indexReader.findWordAsTopic(orderedIncludes);
                case AUTO                   -> indexReader.findWordTopicDynamicMode(orderedIncludes);
            };

        if (query == null) {
            return new IndexQuery(Collections.emptyList());
        }

        query = query.addInclusionFilter(new QueryFilterStepFromPredicate(includePred));

        for (int i = 0; i < orderedIncludes.length; i++) {
            query = query.also(orderedIncludes[i]);
        }

        for (int term : terms.excludes()) {
            query = query.not(term);
        }

        // Run these last, as they'll worst-case cause as many page faults as there are
        // items in the buffer
        return query
                .addInclusionFilter(indexReader.filterForParams(params))
                .build();
    }

    private int compareKeywords(int a, int b) {
        return Long.compare(
                indexReader.numHits(a),
                indexReader.numHits(b)
        );
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

    public int getTotalDocCount() {
        return indexReader.totalDocCount();
    }

    public int getTermFrequency(int id) {
        return (int) indexReader.numHits(id);
    }
}
