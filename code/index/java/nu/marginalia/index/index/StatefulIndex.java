package nu.marginalia.index.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
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
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    private Predicate<LongSet> containsOnly(long[] permitted) {
        LongSet permittedTerms = new LongOpenHashSet(permitted);
        return permittedTerms::containsAll;
    }

    private List<IndexQueryBuilder> createBuilders(CompiledQueryLong query,
                                                   LongFunction<IndexQueryBuilder> builderFactory,
                                                   long[] termPriority) {
        List<LongSet> paths = CompiledQueryAggregates.queriesAggregate(query);

        // Remove any paths that do not contain all prioritized terms, as this means
        // the term is missing from the index and can never be found
        paths.removeIf(containsOnly(termPriority).negate());

        List<QueryBranchWalker> helpers = QueryBranchWalker.create(termPriority, paths);
        List<IndexQueryBuilder> builders = new ArrayList<>();

        for (var helper : helpers) {
            var builder = builderFactory.apply(helper.termId);

            builders.add(builder);

            if (helper.atEnd())
                continue;

            var filters = helper.next().stream()
                            .map(this::createFilter)
                            .toList();

            builder.addInclusionFilterAny(filters);
        }

        return builders;
    }

    private QueryFilterStepIf createFilter(QueryBranchWalker helper) {
        var selfCondition = combinedIndexReader.hasWordFull(helper.termId);
        if (helper.atEnd())
            return selfCondition;

        var nextSteps = helper.next();
        var nextFilters = nextSteps.stream()
                .map(this::createFilter)
                .map(filter -> new QueryFilterAllOf(List.of(selfCondition, filter)))
                .collect(Collectors.toList());

        if (nextFilters.isEmpty())
            return selfCondition;

        if (nextFilters.size() == 1)
            return nextFilters.getFirst();


        return new QueryFilterAnyOf(nextFilters);
    }

    public List<IndexQuery> createQueries(SearchTerms terms, QueryParams params) {

        if (!isLoaded()) {
            logger.warn("Index reader not ready");
            return Collections.emptyList();
        }

        final long[] orderedIncludes = terms.sortedDistinctIncludes(this::compareKeywords);
        final long[] orderedIncludesPrio = terms.sortedDistinctIncludes(this::compareKeywordsPrio);

        List<IndexQueryBuilder> queryHeads = new ArrayList<>(10);

        queryHeads.addAll(createBuilders(terms.compiledQuery(), combinedIndexReader::findFullWord, orderedIncludes));
        queryHeads.addAll(createBuilders(terms.compiledQuery(), combinedIndexReader::findPriorityWord, orderedIncludesPrio));

        List<IndexQuery> queries = new ArrayList<>(10);

        for (var query : queryHeads) {

            for (long term : terms.excludes()) {
                query = query.notFull(term);
            }

            // Run these filter steps last, as they'll worst-case cause as many page faults as there are
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
