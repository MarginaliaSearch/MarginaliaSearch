package nu.marginalia.index.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.DocMetadataList;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.IndexFactory;
import nu.marginalia.index.model.SearchTerms;
import nu.marginalia.index.query.*;
import nu.marginalia.index.query.filter.QueryFilterStepFromPredicate;
import nu.marginalia.index.results.model.ids.TermIdList;
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
public class StatefulIndex {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ReadWriteLock indexReplacementLock = new ReentrantReadWriteLock();
    @NotNull
    private final IndexFactory servicesFactory;
    private final ServiceEventLog eventLog;

    private volatile CombinedIndexReader combinedIndexReader;

    @Inject
    public StatefulIndex(@NotNull IndexFactory servicesFactory,
                         ServiceEventLog eventLog) {
        this.servicesFactory = servicesFactory;
        this.eventLog = eventLog;
    }

    public void init() {
        Lock lock = indexReplacementLock.writeLock();

        try {
            lock.lock();
            logger.info("Initializing index");

            if (combinedIndexReader == null) {
                combinedIndexReader = servicesFactory.getCombinedIndexReader();
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

            if (combinedIndexReader != null)
                combinedIndexReader.close();

            servicesFactory.switchFiles();

            combinedIndexReader = servicesFactory.getCombinedIndexReader();

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


    /** Returns true if the service has initialized */
    public boolean isAvailable() {
        return combinedIndexReader != null;
    }

    /** Stronger version of isAvailable() that also checks that the index is loaded */
    public boolean isLoaded() {
        return combinedIndexReader != null && combinedIndexReader.isLoaded();
    }


    public List<IndexQuery> createQueries(SearchTerms terms, QueryParams params) {

        if (!isLoaded()) {
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
                    var entrySource = combinedIndexReader
                            .findPriorityWord(IndexQueryPriority.BEST, orderedIncludesPrio[i], fetchSizeMultiplier)
                            .alsoPrio(orderedIncludesPrio[j]);
                    queryHeads.add(entrySource);
                }
            }
        }

        // Next consider entries that appear only once in the priority index
        for (var wordId : orderedIncludesPrio) {
            queryHeads.add(combinedIndexReader.findPriorityWord(IndexQueryPriority.GOOD, wordId, fetchSizeMultiplier));
        }

        // Finally consider terms in the full index, but only do this for sufficiently long queries
        // as short queries tend to be too underspecified to produce anything other than CPU warmth
        queryHeads.add(combinedIndexReader.findFullWord(IndexQueryPriority.FALLBACK, orderedIncludes[0], fetchSizeMultiplier));

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

            // Run these last, as they'll worst-case cause as many page faults as there are
            // items in the buffer
            queries.add(query.addInclusionFilter(combinedIndexReader.filterForParams(params)).build());
        }

        return queries;
    }

    private int compareKeywords(long a, long b) {
        return Long.compare(
                combinedIndexReader.numHits(a),
                combinedIndexReader.numHits(b)
        );
    }

    private int compareKeywordsPrio(long a, long b) {
        return Long.compare(
                combinedIndexReader.numHitsPrio(a),
                combinedIndexReader.numHitsPrio(b)
        );
    }

    /** Return an array of encoded document metadata longs corresponding to the
     * document identifiers provided; with metadata for termId.  The input array
     * docs[] *must* be sorted.
     */
    public DocMetadataList getTermMetadata(long termId, CombinedDocIdList docs) {
        return combinedIndexReader.getMetadata(termId, docs);
    }
    public long getDocumentMetadata(long docId) {
        return combinedIndexReader.getDocumentMetadata(docId);
    }

    public int getHtmlFeatures(long docId) {
        return combinedIndexReader.getHtmlFeatures(docId);
    }

    public int getTotalDocCount() {
        return combinedIndexReader.totalDocCount();
    }
    public int getTermFrequency(long id) {
        return (int) combinedIndexReader.numHits(id);
    }

    public int getTermFrequencyPrio(long id) {
        return (int) combinedIndexReader.numHitsPrio(id);
    }
}
