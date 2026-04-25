package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.control.ServiceEventLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** This class holds {@link CombinedIndexReader} and deals with the stateful nature of the index,
 * i.e. it may be possible to reconstruct the index and load a new set of data.
 */
@Singleton
public class StatefulIndex {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ReadWriteLock indexReplacementLock = new ReentrantReadWriteLock();

    @NotNull
    private final IndexFactory servicesFactory;
    private final ServiceEventLog eventLog;

    private volatile CombinedIndexReader combinedIndexReader;

    /** The index has entered a degraded state and needs to be restarted
     * to resolve its issues. */
    private volatile boolean degradedState = false;

    // Hint used to give the index switchover preference in indexReplacementLock acquisition
    private volatile boolean wantSwitchoverHint = false;

    @Inject
    public StatefulIndex(@NotNull IndexFactory servicesFactory,
                         ServiceEventLog eventLog) {
        this.servicesFactory = servicesFactory;
        this.eventLog = eventLog;
    }

    /** For use in testing only */
    public StatefulIndex(CombinedIndexReader combinedIndexReader) {
        this.combinedIndexReader = combinedIndexReader;
        this.servicesFactory = null;
        this.eventLog = null;
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


    /** Additional work to be performed during index switchover,
     * hopefully holding a lock that excludes index queries
     * (but in a degraded state, not guaranteed)
     */
    interface SwitchoverTask {
        void call() throws Exception;
    }

    /** Returns true if the index was successfully switched
     *
     * @param additionalWork additional work to perform during the index switch operation
     *
     * */
    public boolean switchIndex(SwitchoverTask additionalWork) {
        eventLog.logEvent("INDEX-SWITCH-BEGIN", "");
        Lock lock = indexReplacementLock.writeLock();
        try {
            wantSwitchoverHint = true;
            lock.lock();

            CombinedIndexReader oldIndex = combinedIndexReader;

            // We've been unable to close the index,
            // switch the files anyway and enter a degraded state,
            // that should provoke a restart.

            if (oldIndex != null && !oldIndex.close()) {
                eventLog.logEvent("INDEX-SWITCH-DEGRADED", "Failed to close old index");
                servicesFactory.switchFiles();
                try {
                    additionalWork.call();
                }
                catch (Exception ex) {
                    logger.error("Failed to perform additional work", ex);
                }
                combinedIndexReader = null;
                degradedState = true;
                return false;
            }

            servicesFactory.switchFiles();

            CombinedIndexReader nextIndex = servicesFactory.getCombinedIndexReader();

            while (!nextIndex.isLoaded()) {
                LockSupport.parkNanos(100_000);
            }
            combinedIndexReader = nextIndex;

            try {
                additionalWork.call();
            }
            catch (Exception ex) {
                logger.error("Failed to perform additional work", ex);
                degradedState = true;
            }

            eventLog.logEvent("INDEX-SWITCH-OK", "");
        }
        catch (Exception ex) {
            eventLog.logEvent("INDEX-SWITCH-ERR", "");
            logger.error("Uncaught exception", ex);
        }
        finally {
            lock.unlock();
            wantSwitchoverHint = false;
        }

        return true;
    }

    // for tests
    public boolean switchIndex() {
        return switchIndex(() -> {});
    }

    /** Returns true if the service has initialized */
    public boolean isAvailable() {
        return combinedIndexReader != null;
    }

    /** Stronger page of isAvailable() that also checks that the index is loaded */
    public boolean isLoaded() {
        return combinedIndexReader != null && combinedIndexReader.isLoaded();
    }

    /** Returns a reference to the current index.  As long as the reference is not closed,
     * the system guarantees the index will not be closed.  It is very important that this is handled correctly,
     * as we do unsafe and native calls referencing memory mapped regions during index queries,
     * and closing these while the queries execute generally leads to a JVM SIGSEGV.
     */
    public IndexReference get() {

        var indexReadLock = indexReplacementLock.readLock();

        if (wantSwitchoverHint || !indexReadLock.tryLock())
            return new IndexReference(null, null);

        try {
            // grab a reference to avoid TOCTOU scenario
            var currentCIR = combinedIndexReader;
            if (currentCIR == null || !currentCIR.isLoaded())
                return new IndexReference(null, null);

            Lock instanceUseLock = currentCIR.useLock();
            if (instanceUseLock.tryLock()) {
                return new IndexReference(currentCIR, instanceUseLock);
            } else {
                // indexReadLock.readLock being held means this shouldn't be possible,
                // but let's blow up anyway in case there's a regression
                throw new IllegalStateException("Expected to be able to acquire instance use lock");
            }
        }
        finally {
            indexReadLock.unlock();
        }
    }

    public boolean isDegraded() {
        return degradedState;
    }

    public static class IndexReference implements AutoCloseable {
        @Nullable
        private final CombinedIndexReader index;
        @Nullable
        private final Lock useLock;

        public IndexReference(
                @Nullable CombinedIndexReader index,
                @Nullable Lock useLock) {
            this.index = index;
            this.useLock = useLock;
        }

        public boolean isAvailable() {
            return index != null && index.isLoaded();
        }

        @NotNull
        public CombinedIndexReader get() {
            if (index == null || !index.isLoaded())
                throw new IllegalStateException("Index not available");

            return index;
        }

        public void close() {
            if (useLock != null) {
                useLock.unlock();
            }
        }

    }

}
