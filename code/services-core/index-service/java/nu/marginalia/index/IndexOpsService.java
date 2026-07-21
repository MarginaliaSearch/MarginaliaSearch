package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.annotation.CheckReturnValue;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class IndexOpsService {
    private final ReentrantLock opsLock = new ReentrantLock();

    private final StatefulIndex index;

    @Inject
    public IndexOpsService(StatefulIndex index) {
        this.index = index;
    }

    public boolean isBusy() {
        return opsLock.isLocked();
    }

    /** @return true if the index was switched
     *
     * @param additionalWork additional work to perform during the index switch operation
     * */
    public boolean switchIndex(StatefulIndex.SwitchoverTask additionalWork) throws Exception {
        return run(() -> index.switchIndex(additionalWork)).orElse(false);
    }


    @CheckReturnValue
    public <T> Optional<T> run(Callable<T> c) throws Exception {
        if (!opsLock.tryLock())
            return Optional.empty();

        try {
            return Optional.of(c.call());
        }
        finally {
            opsLock.unlock();
        }
    }

}
