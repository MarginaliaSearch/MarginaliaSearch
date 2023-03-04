package nu.marginalia.index;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.svc.IndexOpsService;
import nu.marginalia.index.svc.IndexQueryService;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import nu.marginalia.service.server.Service;
import org.jetbrains.annotations.NotNull;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.concurrent.TimeUnit;

import static spark.Spark.get;

public class IndexService extends Service {

    @NotNull
    private final Initialization init;
    private final IndexOpsService opsService;
    private final SearchIndex searchIndex;


    @Inject
    public IndexService(@Named("service-host") String ip,
                        @Named("service-port") Integer port,
                        Initialization init,
                        MetricsServer metricsServer,
                        IndexOpsService opsService,
                        IndexQueryService indexQueryService,
                        SearchIndex searchIndex
                            )
    {
        super(ip, port, init, metricsServer);
        this.opsService = opsService;
        this.searchIndex = searchIndex;

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

    }


}


