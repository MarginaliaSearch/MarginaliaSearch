package nu.marginalia.query;

import com.google.inject.Inject;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.functions.domainlinks.AggregateDomainLinksService;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import spark.Spark;

import java.io.IOException;
import java.util.List;

public class QueryService extends Service {

    @SneakyThrows
    @Inject
    public QueryService(BaseServiceParams params,
                        AggregateDomainLinksService domainLinksService,
                        QueryGRPCService queryGRPCService,
                        QueryBasicInterface queryBasicInterface)
    {
        super(params,
                () -> Spark.staticFileLocation("/static/"),
                ServicePartition.any(),
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
