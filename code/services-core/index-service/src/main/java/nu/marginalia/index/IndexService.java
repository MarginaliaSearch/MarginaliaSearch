package nu.marginalia.index;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.svc.IndexOpsService;
import nu.marginalia.index.svc.IndexQueryService;
import nu.marginalia.index.svc.IndexSearchSetsService;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static spark.Spark.get;

public class IndexService extends Service {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Initialization init;
    private final IndexOpsService opsService;
    private final SearchIndex searchIndex;

    private final IndexServicesFactory servicesFactory;
    private final IndexSearchSetsService searchSetsService;
    private final ServiceEventLog eventLog;


    @Inject
    public IndexService(BaseServiceParams params,
                        IndexOpsService opsService,
                        IndexQueryService indexQueryService,
                        SearchIndex searchIndex,
                        IndexServicesFactory servicesFactory,
                        IndexSearchSetsService searchSetsService,
                        ServiceEventLog eventLog)
    {
        super(params);

        this.opsService = opsService;
        this.searchIndex = searchIndex;
        this.servicesFactory = servicesFactory;
        this.searchSetsService = searchSetsService;
        this.eventLog = eventLog;

        final Gson gson = GsonFactory.get();

        this.init = params.initialization;

        Spark.post("/search/", indexQueryService::search, gson::toJson);

        Spark.post("/ops/repartition", opsService::repartitionEndpoint);
        Spark.post("/ops/reindex", opsService::reindexEndpoint);

        get("/is-blocked", this::isBlocked, gson::toJson);

        Schedulers.newThread().scheduleDirect(this::initialize, 1, TimeUnit.MICROSECONDS);
    }

    private Object isBlocked(Request request, Response response) {
        return !initialized || opsService.isBusy();
    }

    volatile boolean initialized = false;

    public void initialize() {
        if (!initialized) {
            init.waitReady();
            searchIndex.init();
            initialized = true;
        }

        if (!opsService.run(this::autoConvert)) {
            logger.warn("Auto-convert could not be performed, ops lock busy");
        }
    }

    private void autoConvert() {
        if (!servicesFactory.isConvertedIndexMissing()
            || !servicesFactory.isPreconvertedIndexPresent()
            || Boolean.getBoolean("no-auto-convert")
        ) {
            return;
        }

        try {
            eventLog.logEvent("INDEX-AUTO-CONVERT-BEGIN", "");
            logger.info("Auto-converting");
            searchSetsService.recalculateAll();
            searchIndex.switchIndex();
            eventLog.logEvent("INDEX-AUTO-CONVERT-END", "");
            logger.info("Auto-conversion finished!");
        }
        catch (IOException ex) {
            logger.error("Auto convert failed", ex);
        }
    }


}


