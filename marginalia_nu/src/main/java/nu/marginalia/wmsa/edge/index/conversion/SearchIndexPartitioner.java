package nu.marginalia.wmsa.edge.index.conversion;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.set.hash.TIntHashSet;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static nu.marginalia.wmsa.edge.index.EdgeIndexService.DYNAMIC_BUCKET_LENGTH;

@Singleton
public class SearchIndexPartitioner {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PartitionSet partitionSet;

    private SearchEngineRanking retroRanking = null;
    private SearchEngineRanking smallWebRanking = null;
    private SearchEngineRanking standardRanking = null;
    private SearchEngineRanking specialDomainRanking = null;
    private SearchEngineRanking academiaRanking = null;

    private volatile TIntHashSet goodUrls;

    private final SearchIndexDao dao;
    private final ReadWriteLock rwl = new ReentrantReadWriteLock();

    @Inject
    public SearchIndexPartitioner(SearchIndexDao dao) {
        this.dao = dao;

        if (null == dao) {
            partitionSet = this::yesFilter;
        }
        else {
            partitionSet = this::byPartitionTable;
        }
    }

    public boolean isBusy() {
        var readLock = rwl.readLock();
        try {
            return !readLock.tryLock();
        }
        finally {
            readLock.unlock();
        }
    }

    public void reloadPartitions() {
        if (dao == null) {
            logger.info("No dao = no partition table");
            return;
        }

        logger.info("Fetching URLs");

        if (goodUrls != null) {
            goodUrls.clear();
        }
        goodUrls = dao.goodUrls();

        logger.info("Fetching domains");

        var retroDomains = dao.getRetroDomains();
        var smallWebDomains = dao.getSmallWebDomains();
        var academiaDomains = dao.getAcademiaDomains();
        var standardDomains = dao.getStandardDomains();
        var specialDomains = dao.getSpecialDomains();

        logger.info("Got {} retro domains", retroDomains.size());
        logger.info("Got {} small domains", smallWebDomains.size());
        logger.info("Got {} academia domains", academiaDomains.size());
        logger.info("Got {} standard domains", standardDomains.size());
        logger.info("Got {} special domains", specialDomains.size());

        var lock = rwl.writeLock();
        try {
            lock.lock();
            retroRanking = new SearchEngineRanking(0, retroDomains, 0.2, 1);
            smallWebRanking = new SearchEngineRanking(2, smallWebDomains,  0.15);
            academiaRanking = new SearchEngineRanking(3, academiaDomains, 1);
            standardRanking = new SearchEngineRanking(4, standardDomains, 0.2, 1);
            specialDomainRanking = new SearchEngineRanking(6, specialDomains, 1);
            logger.info("Finished building partitions table");
        }
        finally {
            lock.unlock();
        }
    }

    public boolean isGoodUrl(int urlId) {
        if (goodUrls == null)
            return true;
        return goodUrls.contains(urlId);
    }

    private boolean yesFilter(int domainId, int bucketId) {
        return true;
    }
    private boolean byPartitionTable(int domainId, int bucketId) {
        if (retroRanking.hasBucket(bucketId, domainId))
            return true;
        if (smallWebRanking.hasBucket(bucketId, domainId))
            return true;
        if (academiaRanking.hasBucket(bucketId, domainId))
            return true;
        if (standardRanking.hasBucket(bucketId, domainId))
            return true;
        if (specialDomainRanking.hasBucket(bucketId, domainId))
            return true;

        return DYNAMIC_BUCKET_LENGTH == bucketId;
    }

    @SneakyThrows
    public Lock getReadLock() {
        return rwl.readLock();
    }
    public boolean filterUnsafe(int domainId, int bucketId) {
        return partitionSet.test(domainId, bucketId);
    }

    @Deprecated
    public boolean filter(int domainId, int bucketId) {
        var lock = rwl.readLock();
        try {
            lock.lock();
            return partitionSet.test(domainId, bucketId);
        }
        finally {
            lock.unlock();
        }
    }

    public int translateId(int bucketId, int id) {
        if (retroRanking != null && retroRanking.ownsBucket(bucketId)) {
            return retroRanking.translateId(id);
        }
        if (smallWebRanking != null && smallWebRanking.ownsBucket(bucketId)) {
            return smallWebRanking.translateId(id);
        }
        if (academiaRanking != null && academiaRanking.ownsBucket(bucketId)) {
            return academiaRanking.translateId(id);
        }
        if (standardRanking != null && standardRanking.ownsBucket(bucketId)) {
            return standardRanking.translateId(id);
        }
        if (specialDomainRanking != null && specialDomainRanking.ownsBucket(bucketId)) {
            return specialDomainRanking.translateId(id);
        }
        if (retroRanking != null) {
            return retroRanking.translateId(id);
        }
        return id;
    }

    interface PartitionSet {
        boolean test(int domainId, int bucketId);
    }
}
