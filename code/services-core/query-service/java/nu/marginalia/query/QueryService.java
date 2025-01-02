package nu.marginalia.query;

import com.google.inject.Inject;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.linkgraph.AggregateLinkGraphService;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.SparkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.IOException;
import java.util.List;

public class QueryService extends SparkService {

    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);

    @Inject
    public QueryService(BaseServiceParams params,
                        AggregateLinkGraphService domainLinksService,
                        QueryGRPCService queryGRPCService,
                        QueryBasicInterface queryBasicInterface)
            throws Exception
    {
        super(params,
                () -> Spark.staticFileLocation("/static/"),
                ServicePartition.any(),
                List.of(queryGRPCService, domainLinksService));


        Spark.get("/search", queryBasicInterface::handleBasic);

        if (!Boolean.getBoolean("noQdebug")) {
            Spark.get("/qdebug", queryBasicInterface::handleAdvanced);
        }

        Spark.exception(Exception.class, (e, request, response) -> {
            response.status(500);

            logger.info("Exception in query service", e);

            try {
                e.printStackTrace(response.raw().getWriter());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

}
