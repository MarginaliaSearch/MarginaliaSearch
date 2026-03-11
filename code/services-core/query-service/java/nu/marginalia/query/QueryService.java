package nu.marginalia.query;

import com.google.inject.Inject;
import io.jooby.Jooby;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.linkgraph.AggregateLinkGraphService;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class QueryService extends JoobyService {

    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);

    private final QueryBasicInterface queryBasicInterface;

    @Inject
    public QueryService(BaseServiceParams params,
                        AggregateLinkGraphService domainLinksService,
                        QueryGRPCService queryGRPCService,
                        QueryBasicInterface queryBasicInterface)
            throws Exception
    {
        super(params,
                List.of(queryGRPCService, domainLinksService),
                List.of());

        this.queryBasicInterface = queryBasicInterface;
    }

    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        jooby.get("/search", queryBasicInterface::handleBasic);

        if (!Boolean.getBoolean("noQdebug")) {
            jooby.get("/qdebug", queryBasicInterface::handleAdvanced);
        }

        jooby.error(Exception.class, (ctx, cause, code) -> {
            logger.info("Exception in query service", cause);

            try {
                ctx.setResponseCode(500);
                ctx.send(cause.toString());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

}
