package nu.marginalia.index.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.longs.*;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.index.query.filter.QueryFilterAllOf;
import nu.marginalia.index.query.filter.QueryFilterAnyOf;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.DocMetadataList;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.IndexFactory;
import nu.marginalia.index.model.SearchTerms;
import nu.marginalia.index.query.*;
import nu.marginalia.service.control.ServiceEventLog;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

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

        List<IndexQueryBuilder> queryHeads = new ArrayList<>(10);

        final long[] termPriority = terms.sortedDistinctIncludes(this::compareKeywords);
        List<LongSet> paths = CompiledQueryAggregates.queriesAggregate(terms.compiledQuery());

        // Remove any paths that do not contain all prioritized terms, as this means
        // the term is missing from the index and can never be found
        paths.removeIf(containsAll(termPriority).negate());

        for (var path : paths) {
            LongList elements = new LongArrayList(path);

            elements.sort((a, b) -> {
                for (int i = 0; i < termPriority.length; i++) {
                    if (termPriority[i] == a)
                        return -1;
                    if (termPriority[i] == b)
                        return 1;
                }
                return 0;
            });

            var head = combinedIndexReader.findFullWord(elements.getLong(0));
            for (int i = 1; i < elements.size(); i++) {
                head.addInclusionFilter(combinedIndexReader.hasWordFull(elements.getLong(i)));
            }

            queryHeads.add(head);
        }

        // Add additional conditions to the query heads
        for (var query : queryHeads) {

            // Advice terms are a special case, mandatory but not ranked, and exempt from re-writing
            for (long term : terms.advice()) {
                query = query.also(term);
            }

            for (long term : terms.excludes()) {
                query = query.not(term);
            }

            // Run these filter steps last, as they'll worst-case cause as many page faults as there are
            // items in the buffer
            query.addInclusionFilter(combinedIndexReader.filterForParams(params));
        }

        return queryHeads
                .stream()
                .map(IndexQueryBuilder::build)
                .toList();
    }

    /** Recursively create a filter step based on the QBW and its children */
    private QueryFilterStepIf createFilter(QueryBranchWalker walker, int depth) {

        // Create a filter for the current termId
        final QueryFilterStepIf ownFilterCondition = ownFilterCondition(walker, depth);

        var childSteps = walker.next();
        if (childSteps.isEmpty()) // no children, and so we're satisfied with just a single filter condition
            return ownFilterCondition;

        // If there are children, we append the filter conditions for each child as an anyOf condition
        // to the current filter condition

        List<QueryFilterStepIf> combinedFilters = new ArrayList<>();

        for (var step : childSteps) {
            // Recursion will be limited to a fairly shallow stack depth due to how the queries are constructed.
            var childFilter = createFilter(step, depth+1);
            combinedFilters.add(new QueryFilterAllOf(ownFilterCondition, childFilter));
        }

        // Flatten the filter conditions if there's only one branch
        if (combinedFilters.size() == 1)
            return combinedFilters.getFirst();
        else
            return new QueryFilterAnyOf(combinedFilters);
    }

    /** Create a filter condition based on the termId associated with the QBW */
    private QueryFilterStepIf ownFilterCondition(QueryBranchWalker walker, int depth) {
        if (depth < 2) {
            // At shallow depths we prioritize terms that appear in the priority index,
            // to increase the odds we find "good" results before the execution timer runs out
            return new QueryFilterAnyOf(
                    combinedIndexReader.hasWordPrio(walker.termId),
                    combinedIndexReader.hasWordFull(walker.termId)
            );
        } else {
            return combinedIndexReader.hasWordFull(walker.termId);
        }
    }

    private Predicate<LongSet> containsAll(long[] permitted) {
        LongSet permittedTerms = new LongOpenHashSet(permitted);
        return permittedTerms::containsAll;
    }

    private int compareKeywords(long a, long b) {
        return Long.compare(
                combinedIndexReader.numHits(a),
                combinedIndexReader.numHits(b)
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
