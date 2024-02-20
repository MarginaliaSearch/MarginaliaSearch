package nu.marginalia.query;

import com.google.inject.Inject;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import spark.Spark;

import java.io.IOException;
import java.util.List;

public class QueryService extends Service {

    private static final Histogram wmsa_qs_query_time_rest = Histogram.build()
            .name("wmsa_qs_query_time_rest")
            .linearBuckets(0.05, 0.05, 15)
            .help("QS-side query time (REST endpoint)")
            .register();


    @SneakyThrows
    @Inject
    public QueryService(BaseServiceParams params,
                        QueryGRPCDomainLinksService domainLinksService,
                        QueryGRPCService queryGRPCService,
                        QueryBasicInterface queryBasicInterface)
    {
        super(params,
                () -> Spark.staticFileLocation("/static/"),
                List.of(queryGRPCService, domainLinksService));


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
