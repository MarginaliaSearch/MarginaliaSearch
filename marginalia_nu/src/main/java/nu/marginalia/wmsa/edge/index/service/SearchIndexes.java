package nu.marginalia.wmsa.edge.index.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.radix.EdgeIndexBucket;
import nu.marginalia.wmsa.edge.index.service.dictionary.DictionaryReader;
import nu.marginalia.wmsa.edge.index.service.index.SearchIndexWriterImpl;
import nu.marginalia.wmsa.edge.index.service.query.SearchIndexPartitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.locks.ReentrantLock;

import static nu.marginalia.wmsa.edge.index.EdgeIndexService.DYNAMIC_BUCKET_LENGTH;

@Singleton
public class SearchIndexes {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EdgeIndexBucket[] buckets
            = new EdgeIndexBucket[DYNAMIC_BUCKET_LENGTH + 1];
    private final IndexServicesFactory servicesFactory;
    private final SearchIndexPartitioner partitioner;

    private final ReentrantLock opsLock = new ReentrantLock(false);

    private final SearchIndexWriterImpl primaryIndexWriter;
    private final SearchIndexWriterImpl secondaryIndexWriter;
    private DictionaryReader dictionaryReader = null;

    @Inject
    public SearchIndexes(IndexServicesFactory servicesFactory, SearchIndexPartitioner partitioner) {
        this.servicesFactory = servicesFactory;
        this.partitioner = partitioner;

        this.primaryIndexWriter = servicesFactory.getIndexWriter(0);
        this.secondaryIndexWriter = servicesFactory.getIndexWriter(1);

        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = servicesFactory.createIndexBucket(i);
        }
    }

    public boolean repartition() {

        if (!opsLock.tryLock()) {
            return false;
        }
        try {
            partitioner.reloadPartitions();
        }
        finally {
            opsLock.unlock();
        }

        return true;
    }

    public boolean preconvert() {

        if (!opsLock.tryLock()) {
            return false;
        }
        try {
            buckets[0].preconvert();
        }
        finally {
            opsLock.unlock();
        }

        return true;
    }

    public boolean reindex(int id) {

        if (!opsLock.tryLock()) {
            return false;
        }
        try {
            buckets[id].switchIndex();
        }
        finally {
            opsLock.unlock();
        }

        return true;
    }

    public boolean reindexAll() {
        if (!opsLock.tryLock()) {
            return false;
        }
        try {
            for (var bucket : buckets) {
                bucket.switchIndex();
            }
        } finally {
            opsLock.unlock();
        }

        return true;
    }

    @Nullable
    public DictionaryReader getDictionaryReader() {
        return dictionaryReader;
    }


    public boolean isBusy() {
        return partitioner.isBusy();
    }

    public void initialize(Initialization init) {

        logger.info("Waiting for init");
        init.waitReady();

        opsLock.lock();
        try {
            logger.info("Initializing buckets");
            for (EdgeIndexBucket bucket : buckets) {
                bucket.init();
            }

            logger.info("Initializing dictionary reader");
            dictionaryReader = servicesFactory.getDictionaryReader();
        }
        finally {
            opsLock.unlock();
        }
    }

    public SearchIndexWriterImpl getIndexWriter(int idx) {
        if (idx == 0) {
            return primaryIndexWriter;
        }
        else {
            return secondaryIndexWriter;
        }
    }

    public EdgeIndexBucket getBucket(int bucketId) {
        return buckets[bucketId];
    }
    public boolean isValidBucket(int bucketId) {
        return bucketId >= 0 && bucketId < buckets.length;
    }

}
