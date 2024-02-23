package nu.marginalia.api.searchquery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Summary;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;

@Singleton
public class QueryClient  {

    private static final Summary wmsa_qs_api_search_time = Summary.build()
            .name("wmsa_qs_api_search_time")
            .help("query service search time")
            .register();

    private final GrpcSingleNodeChannelPool<QueryApiGrpc.QueryApiBlockingStub> queryApiPool;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public QueryClient(GrpcChannelPoolFactory channelPoolFactory) {
        this.queryApiPool = channelPoolFactory.createSingle(
                ServiceKey.forGrpcApi(QueryApiGrpc.class, ServicePartition.any()),
                QueryApiGrpc::newBlockingStub);
    }

    @CheckReturnValue
    public QueryResponse search(QueryParams params) {
        var query = QueryProtobufCodec.convertQueryParams(params);

        return wmsa_qs_api_search_time.time(() ->
                QueryProtobufCodec.convertQueryResponse(
                        queryApiPool.call(QueryApiGrpc.QueryApiBlockingStub::query).run(query)
                )
        );
    }

}
