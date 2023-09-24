package nu.marginalia.index.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.query.*;
import nu.marginalia.index.query.filter.QueryFilterStepFromPredicate;
import nu.marginalia.index.svc.IndexSearchSetsService;
import nu.marginalia.service.control.ServiceEventLog;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private final ServiceEventLog eventLog;

    @Inject
    public SearchIndex(@NotNull IndexServicesFactory servicesFactory,
                       IndexSearchSetsService searchSetsService,
                       ServiceEventLog eventLog) {
        this.servicesFactory = servicesFactory;
        this.searchSetsService = searchSetsService;
        this.eventLog = eventLog;
    }

    public void init() {
        Lock lock = indexReplacementLock.writeLock();

        try {
            lock.lock();
            logger.info("Initializing index");

            if (indexReader == null) {
                indexReader = servicesFactory.getSearchIndexReader();
                eventLog.logEvent("INDEX-INIT", "Index loaded");
            }
            else {
                eventLog.logEvent("INDEX-INIT", "No index loaded");
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
        eventLog.logEvent("INDEX-SWITCH-BEGIN", "");
        Lock lock = indexReplacementLock.writeLock();
        try {
            lock.lock();

            if (indexReader != null)
                indexReader.close();

            servicesFactory.switchFiles();

            indexReader = servicesFactory.getSearchIndexReader();

            eventLog.logEvent("INDEX-SWITCH-OK", "");
        }
        catch (Exception ex) {
            eventLog.logEvent("INDEX-SWITCH-ERR", "");
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


    public List<IndexQuery> createQueries(SearchIndexSearchTerms terms, IndexQueryParams params, LongPredicate includePred) {

        if (!isAvailable()) {
            logger.warn("Index reader not ready");
            return Collections.emptyList();
        }

        final long[] orderedIncludes = terms.sortedDistinctIncludes(this::compareKeywords);
        final long[] orderedIncludesPrio = terms.sortedDistinctIncludes(this::compareKeywordsPrio);

        List<IndexQueryBuilder> queryHeads = new ArrayList<>(10);
        List<IndexQuery> queries = new ArrayList<>(10);

        // Fetch more results than specified for short queries, as the query itself is cheap and the
        // priority index may contain a considerable amount of less interesting results
        final int fetchSizeMultiplier;
        if (orderedIncludes.length == 1) fetchSizeMultiplier = 4;
        else fetchSizeMultiplier = 1;

        // To ensure that good results are processed first, create query heads for the priority index that filter for terms
        // that contain pairs of two search terms
        if (orderedIncludesPrio.length > 1) {
            for (int i = 0; i + 1 < orderedIncludesPrio.length; i++) {
                for (int j = i + 1; j < orderedIncludesPrio.length; j++) {
                    var entrySource = indexReader
                            .findPriorityWord(IndexQueryPriority.BEST, orderedIncludesPrio[i], fetchSizeMultiplier)
                            .alsoPrio(orderedIncludesPrio[j]);
                    queryHeads.add(entrySource);
                }
            }
        }

        // Next consider entries that appear only once in the priority index
        for (var wordId : orderedIncludesPrio) {
            queryHeads.add(indexReader.findPriorityWord(IndexQueryPriority.GOOD, wordId, fetchSizeMultiplier));
        }

        // Finally consider terms in the full index, but only do this for sufficiently long queries
        // as short queries tend to be too underspecified to produce anything other than CPU warmth
        queryHeads.add(indexReader.findFullWord(IndexQueryPriority.FALLBACK, orderedIncludes[0], fetchSizeMultiplier));

        for (var query : queryHeads) {
            if (query == null) {
                return Collections.emptyList();
            }

            for (long orderedInclude : orderedIncludes) {
                query = query.alsoFull(orderedInclude);
            }

            for (long term : terms.excludes()) {
                query = query.notFull(term);
            }

            // This filtering step needs to happen only on terms that have passed all term-based filtering steps,
            // it's essentially a memoization of the params filtering job which is relatively expensive
            query = query.addInclusionFilter(new QueryFilterStepFromPredicate(includePred));

            // Run these last, as they'll worst-case cause as many page faults as there are
            // items in the buffer
            queries.add(query.addInclusionFilter(indexReader.filterForParams(params)).build());
        }

        return queries;
    }

    private int compareKeywords(long a, long b) {
        return Long.compare(
                indexReader.numHits(a),
                indexReader.numHits(b)
        );
    }

    private int compareKeywordsPrio(long a, long b) {
        return Long.compare(
                indexReader.numHitsPrio(a),
                indexReader.numHitsPrio(b)
        );
    }

    /** Return an array of encoded document metadata longs corresponding to the
     * document identifiers provided; with metadata for termId.  The input array
     * docs[] *must* be sorted.
     */
    public long[] getTermMetadata(long termId, long[] docs) {
        return indexReader.getMetadata(termId, docs);
    }

    public long getDocumentMetadata(long docId) {
        return indexReader.getDocumentMetadata(docId);
    }
    public int getHtmlFeatures(long docId) {
        return indexReader.getHtmlFeatures(docId);
    }

    public int getTotalDocCount() {
        return indexReader.totalDocCount();
    }

    public int getTermFrequency(long id) {
        return (int) indexReader.numHits(id);
    }
    public int getTermFrequencyPrio(long id) {
        return (int) indexReader.numHitsPrio(id);
    }
}
