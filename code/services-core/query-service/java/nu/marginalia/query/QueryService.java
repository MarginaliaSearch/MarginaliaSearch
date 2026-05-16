package nu.marginalia.query;

import com.google.inject.Inject;
import io.jooby.Cookie;
import io.jooby.Jooby;

import io.jooby.SessionStore;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.linkgraph.AggregateLinkGraphService;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import nu.marginalia.service.server.SparkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

public class QueryService extends JoobyService {

    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);

    private final QueryGRPCService queryGRPCService;
    private final QueryDebugInterface queryDebugInterface;
    private final QueryWebApi queryWebApi;

    @Inject
    public QueryService(BaseServiceParams params,
                        AggregateLinkGraphService domainLinksService,
                        QueryGRPCService queryGRPCService,
                        QueryDebugInterface queryDebugInterface,
                        QueryWebApi queryWebApi)
            throws Exception
    {
        super(params,
                List.of(queryGRPCService, domainLinksService),
                List.of());

        this.queryGRPCService = queryGRPCService;
        this.queryDebugInterface = queryDebugInterface;
        this.queryWebApi = queryWebApi;
    }

    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        jooby.setSessionStore(SessionStore.memory(Cookie.session("marginalia-session")));

        jooby.get("/search", queryWebApi::handleApiSearch);
        jooby.get("/site/{domain}/availability", queryWebApi::handleDomainAvailability);
        jooby.get("/site/{domain}", queryWebApi::handleDomainInfo);

        if (!Boolean.getBoolean("noQdebug")) {
            jooby.get("/qdebug", queryDebugInterface::handleAdvanced);
        }

        jooby.error(Exception.class, (ctx, e, code) -> {
            ctx.setResponseCode(500);
            logger.info("Exception in query service", e);

            try (var osw = new PrintWriter(new OutputStreamWriter(ctx.responseStream()))) {
                e.printStackTrace(osw);
            }
        });
    }

}
