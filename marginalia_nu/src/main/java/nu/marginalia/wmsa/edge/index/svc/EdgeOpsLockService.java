package nu.marginalia.wmsa.edge.index.svc;

import javax.annotation.CheckReturnValue;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class EdgeOpsLockService {
    public ReentrantLock opsLock = new ReentrantLock();

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

    public boolean isLocked() {
        return opsLock.isLocked();
    }
}
