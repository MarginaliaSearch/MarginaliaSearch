package nu.marginalia.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.index.index.SearchIndex;
import spark.Request;
import spark.Response;
import spark.Spark;

import javax.annotation.CheckReturnValue;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class IndexOpsService {
    private final ReentrantLock opsLock = new ReentrantLock();

    private final SearchIndex index;
    private final IndexSearchSetsService searchSetService;

    @Inject
    public IndexOpsService(SearchIndex index,
                           IndexSearchSetsService searchSetService) {
        this.index = index;
        this.searchSetService = searchSetService;
    }

    public boolean isBusy() {
        return opsLock.isLocked();
    }

    public boolean repartition() {
        return run(searchSetService::recalculateAll);
    }
    public boolean reindex() throws Exception {
        return run(index::switchIndex).isPresent();
    }

    public Object repartitionEndpoint(Request request, Response response) throws Exception {

        if (!run(searchSetService::recalculateAll)) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    public Object reindexEndpoint(Request request, Response response) throws Exception {
        if (!run(index::switchIndex).isPresent()) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
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

