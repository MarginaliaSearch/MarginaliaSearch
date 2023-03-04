package nu.marginalia.index;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.svc.IndexOpsService;
import nu.marginalia.index.svc.IndexQueryService;
import nu.marginalia.index.svc.IndexSearchSetsService;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import nu.marginalia.service.server.Service;
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


    @Inject
    public IndexService(@Named("service-host") String ip,
                        @Named("service-port") Integer port,
                        Initialization init,
                        MetricsServer metricsServer,
                        IndexOpsService opsService,
                        IndexQueryService indexQueryService,
                        SearchIndex searchIndex,
                        IndexServicesFactory servicesFactory,
                        IndexSearchSetsService searchSetsService)
    {
        super(ip, port, init, metricsServer);
        this.opsService = opsService;
        this.searchIndex = searchIndex;
        this.servicesFactory = servicesFactory;
        this.searchSetsService = searchSetsService;

        final Gson gson = GsonFactory.get();

        this.init = init;

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
        if (!servicesFactory.isConvertedIndexMissing() || !servicesFactory.isPreconvertedIndexPresent()) {
            return;
        }
        try {
            searchSetsService.recalculateAll();
            searchIndex.switchIndex();
        }
        catch (IOException ex) {
            logger.error("Auto convert failed", ex);
        }
    }


}


