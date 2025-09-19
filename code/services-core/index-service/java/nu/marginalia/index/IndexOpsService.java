package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.searchset.SearchSetsService;

import javax.annotation.CheckReturnValue;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class IndexOpsService {
    private final ReentrantLock opsLock = new ReentrantLock();

    private final StatefulIndex index;
    private final SearchSetsService searchSetService;

    @Inject
    public IndexOpsService(StatefulIndex index,
                           SearchSetsService searchSetService) {
        this.index = index;
        this.searchSetService = searchSetService;
    }

    public boolean isBusy() {
        return opsLock.isLocked();
    }

    public boolean rerank() {
        return run(searchSetService::recalculatePrimaryRank);
    }

    public boolean repartition() {
        return run(searchSetService::recalculateSecondary);
    }

    public boolean switchIndex() throws Exception {
        return run(index::switchIndex).isPresent();
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


    @CheckReturnValue
    public boolean run(Runnable r) {
        if (!opsLock.tryLock())
            return false;
        try {
            r.run();
            return true;
        }
        finally {
            opsLock.unlock();
        }
    }


}

