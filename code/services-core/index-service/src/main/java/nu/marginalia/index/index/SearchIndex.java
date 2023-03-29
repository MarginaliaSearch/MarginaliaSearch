package nu.marginalia.index.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.index.query.ReverseIndexEntrySourceBehavior;
import nu.marginalia.index.query.filter.QueryFilterStepFromPredicate;
import nu.marginalia.index.svc.IndexSearchSetsService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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


    public List<IndexQuery> createQueries(SearchIndexSearchTerms terms, IndexQueryParams params, LongPredicate includePred) {

        if (!isAvailable()) {
            logger.warn("Index reader not ready");
            return Collections.emptyList();
        }

        final int[] orderedIncludes = terms.sortedDistinctIncludes(this::compareKeywords);

        List<IndexQueryBuilder> queryHeads = new ArrayList<>(10);
        List<IndexQuery> queries = new ArrayList<>(10);

        // To ensure that good results are processed first, create query heads for the priority index that filter for terms
        // that contain pairs of two search terms
        if (orderedIncludes.length > 1) {
            for (int i = 0; i + 1 < orderedIncludes.length; i++) {
                var remainingWords = Arrays.copyOfRange(orderedIncludes, i+1, orderedIncludes.length);
                var entrySource = indexReader
                        .findPriorityWord(orderedIncludes[i])
                        .alsoPrioAnyOf(remainingWords);
                queryHeads.add(entrySource);
            }
        }

        // Next consider entries that appear only once in the priority index
        for (var wordId : orderedIncludes) {
            queryHeads.add(indexReader.findPriorityWord(wordId));
        }

        // Finally consider terms in the full index
        queryHeads.add(indexReader.findFullWord(orderedIncludes[0], ReverseIndexEntrySourceBehavior.DO_PREFER));

        for (var query : queryHeads) {
            if (query == null) {
                return Collections.emptyList();
            }


            for (int orderedInclude : orderedIncludes) {
                query = query.alsoFull(orderedInclude);
            }

            for (int term : terms.excludes()) {
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
