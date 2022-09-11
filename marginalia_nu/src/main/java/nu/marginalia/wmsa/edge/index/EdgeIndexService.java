package nu.marginalia.wmsa.edge.index;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexLexiconService;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexOpsService;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexQueryService;
import org.jetbrains.annotations.NotNull;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.concurrent.TimeUnit;

import static spark.Spark.get;

public class EdgeIndexService extends Service {

    @NotNull
    private final Initialization init;
    private final SearchIndexes indexes;

    public static final int DYNAMIC_BUCKET_LENGTH = 7;


    @Inject
    public EdgeIndexService(@Named("service-host") String ip,
                            @Named("service-port") Integer port,
                            Initialization init,
                            MetricsServer metricsServer,
                            SearchIndexes indexes,

                            EdgeIndexOpsService opsService,
                            EdgeIndexLexiconService lexiconService,
                            EdgeIndexQueryService indexQueryService)
    {
        super(ip, port, init, metricsServer);

        final Gson gson = GsonFactory.get();

        this.init = init;
        this.indexes = indexes;

        Spark.post("/words/", lexiconService::putWords);

        Spark.post("/search/", indexQueryService::search, gson::toJson);
        Spark.post("/search-domain/", indexQueryService::searchDomain, gson::toJson);

        Spark.get("/dictionary/*", lexiconService::getWordId, gson::toJson);

        Spark.post("/ops/repartition", opsService::repartitionEndpoint);
        Spark.post("/ops/preconvert", opsService::preconvertEndpoint);
        Spark.post("/ops/reindex/:id", opsService::reindexEndpoint);

        get("/is-blocked", this::isBlocked, gson::toJson);

        Schedulers.newThread().scheduleDirect(this::initialize, 1, TimeUnit.MICROSECONDS);
    }

    private Object isBlocked(Request request, Response response) {
        return indexes.isBusy() || !initialized;
    }

    volatile boolean initialized = false;
    public void initialize() {
        if (!initialized) {
            init.waitReady();
            initialized = true;
        }
        else {
            return;
        }
        indexes.initialize(init);
    }


}


