package nu.marginalia.wmsa.edge.index;

import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriter;
import nu.marginalia.wmsa.edge.index.model.EdgeIndexSearchTerms;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexReader;
import nu.marginalia.wmsa.edge.index.reader.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.reader.query.Query;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;

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

    public LongStream findHotDomainsForKeyword(IndexBlock block, int wordId, int queryDepth, int minHitCount, int maxResults) {
        return indexReader.findHotDomainsForKeyword(block, wordId, queryDepth, minHitCount, maxResults);
    }

    public LongStream getQuery(IndexBlock block, LongPredicate filter, IndexSearchBudget budget, EdgeIndexSearchTerms searchTerms) {
        if (null == indexReader) {
            logger.warn("Index reader not neady {}", block);
            return LongStream.empty();
        }

        var orderedIncludes = searchTerms.includes
                .stream()
                .sorted(Comparator.comparingLong(i -> indexReader.numHits(block, i)))
                .distinct()
                .mapToInt(Integer::intValue)
                .toArray();

        Query query;

        if (orderedIncludes.length == 1) {
            query = indexReader.findUnderspecified(block, budget, filter, orderedIncludes[0]);
        }
        else {
            query = indexReader.findWord(block, budget, filter, orderedIncludes[0]);
        }
        int i;
        for (i = 1; (i < 3 && i < orderedIncludes.length) || i < orderedIncludes.length-1; i++) {
            query = query.alsoCached(orderedIncludes[i]);
        }
        for (; i < orderedIncludes.length; i++) {
            query = query.also(orderedIncludes[i]);
        }
        for (int term : searchTerms.excludes) {
            query = query.not(term);
        }

        return query.stream();
    }


    public IndexBlock getTermScore(int termId, long urlId) {
        return indexReader.getBlockForResult(termId, urlId);
    }

    public boolean isTermInBucket(IndexBlock block, int termId, long urlId) {
        return indexReader.isTermInBucket(block, termId, urlId);
    }
}
