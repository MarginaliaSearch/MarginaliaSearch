package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.query.svc.QueryFactory;
import nu.marginalia.service.NodeConfigurationWatcher;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import spark.Spark;

import java.io.IOException;
import java.util.List;

public class QueryService extends Service {

    private final IndexClient indexClient;
    private final NodeConfigurationWatcher nodeWatcher;
    private final Gson gson;
    private final DomainBlacklist blacklist;
    private final QueryFactory queryFactory;

    private static final Histogram wmsa_qs_query_time_rest = Histogram.build()
            .name("wmsa_qs_query_time_rest")
            .linearBuckets(0.05, 0.05, 15)
            .help("QS-side query time (REST endpoint)")
            .register();


    @SneakyThrows
    @Inject
    public QueryService(BaseServiceParams params,
                        IndexClient indexClient,
                        NodeConfigurationWatcher nodeWatcher,
                        QueryGRPCDomainLinksService domainLinksService,
                        QueryGRPCService queryGRPCService,
                        Gson gson,
                        DomainBlacklist blacklist,
                        QueryBasicInterface queryBasicInterface,
                        QueryFactory queryFactory)
    {
        super(params,
                () -> Spark.staticFileLocation("/static/"),
                List.of(queryGRPCService, domainLinksService));

        this.indexClient = indexClient;
        this.nodeWatcher = nodeWatcher;
        this.gson = gson;
        this.blacklist = blacklist;
        this.queryFactory = queryFactory;

        Spark.get("/public/search", queryBasicInterface::handle);

        Spark.exception(Exception.class, (e, request, response) -> {
            response.status(500);
            try {
                e.printStackTrace(response.raw().getWriter());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

}
