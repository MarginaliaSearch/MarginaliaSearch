package nu.marginalia.wmsa.edge.index;

import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriter;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexReader;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQuery;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQueryFactory;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQueryParams;
import nu.marginalia.wmsa.edge.index.svc.query.ResultDomainDeduplicator;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepFromPredicate;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryRankLimitingFilter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongPredicate;

public class EdgeIndexBucket {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile SearchIndexReader indexReader;

    private final ReadWriteLock indexReplacementLock = new ReentrantReadWriteLock();

    @NotNull
    private final IndexServicesFactory servicesFactory;
    private final EdgeIndexControl indexControl;
    private final SearchIndexJournalWriter writer;

    private final int id;

    public EdgeIndexBucket(@NotNull IndexServicesFactory servicesFactory, EdgeIndexControl indexControl, int id) {
        this.servicesFactory = servicesFactory;
        this.indexControl = indexControl;
        this.id = id;

        writer = servicesFactory.getIndexWriter(0);
    }

    public void init() {
        Lock lock = indexReplacementLock.writeLock();
        try {
            lock.lock();
            logger.info("Initializing bucket {}", id);

            if (indexReader == null) {
                indexReader = servicesFactory.getIndexReader(id);
            }

        }
        catch (Exception ex) {
            logger.error("Uncaught exception", ex);
        }
        finally {
            lock.unlock();
        }
    }

    public void preconvert() {

        writer.forceWrite();
        writer.flushWords();

        servicesFactory.getIndexPreconverter();

        System.runFinalization();
        System.gc();

    }
    public void switchIndex() {

        indexControl.regenerateIndex(id);

        Lock lock = indexReplacementLock.writeLock();
        try {
            lock.lock();

            indexControl.switchIndexFiles(id);

            if (indexReader != null) {
                indexReader.close();
            }

            indexReader = servicesFactory.getIndexReader(id);

        }
        catch (Exception ex) {
            logger.error("Uncaught exception", ex);
        }
        finally {
            lock.unlock();
        }
    }


    public boolean isAvailable() {
        return indexReader != null;
    }

    public IndexQuery getQuery(LongPredicate filter, IndexQueryParams params) {

        if (null == indexReader) {
            logger.warn("Index reader not neady {}", params.block());
            return new IndexQuery(Collections.emptyList());
        }

        final int[] orderedIncludes = params.searchTerms()
                .sortedDistinctIncludes((a, b) -> compareKeywords(params.block(), a, b));

        IndexQueryFactory.IndexQueryBuilder query = createQueryBuilder(orderedIncludes[0], params);

        if (query == null) {
            return new IndexQuery(Collections.emptyList());
        }

        query.addInclusionFilter(new QueryFilterStepFromPredicate(filter));
        if (params.rankLimit() != null) {
            query.addInclusionFilter(new QueryRankLimitingFilter(params.rankLimit()));
        }

        for (int i = 1; i < orderedIncludes.length; i++) {
            query = query.also(orderedIncludes[i]);
        }

        for (int term : params.searchTerms().excludes()) {
            query = query.not(term);
        }

        return query.build();
    }

    private IndexQueryFactory.IndexQueryBuilder createQueryBuilder(int firstKeyword, IndexQueryParams params) {

        if (params.targetDomains() != null && !params.targetDomains().isEmpty()) {
            return indexReader.findWordForDomainList(params.block(), params.targetDomains(), firstKeyword);
        }
        return indexReader.findWord(params.block(), params.qualityLimit(), firstKeyword);

    }

    private int compareKeywords(IndexBlock block, int a, int b) {
        return Long.compare(
                indexReader.numHits(block, a),
                indexReader.numHits(block, b)
        );
    }


    public IndexQuery getDomainQuery(int wordId, ResultDomainDeduplicator localFilter) {
        var query = indexReader.findDomain(wordId);

        query.addInclusionFilter(new QueryFilterStepFromPredicate(localFilter::filterRawValue));

        return query;
    }

    /** Replaces the values of ids with their associated metadata, or 0L if absent */
    public long[] getMetadata(IndexBlock block, int termId, long[] ids) {
        return indexReader.getMetadata(block, termId, ids);
    }
}
