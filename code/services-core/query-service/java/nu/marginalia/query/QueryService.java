package nu.marginalia.query;

import com.google.inject.Inject;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.linkgraph.AggregateLinkGraphService;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.JoobyService;

import java.util.List;

public class QueryService extends JoobyService {


    @Inject
    public QueryService(BaseServiceParams params,
                        AggregateLinkGraphService domainLinksService,
                        QueryGRPCService queryGRPCService,
                        QueryBasicInterface queryBasicInterface,
                        Initialization initialization)
    {
        super(params,
                ServicePartition.any(),
                initialization,
                List.of(queryGRPCService, domainLinksService),
                List.of(new QueryBasicInterface_(queryBasicInterface))
                );
    }

}
