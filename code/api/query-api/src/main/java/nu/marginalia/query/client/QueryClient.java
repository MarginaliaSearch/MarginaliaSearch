package nu.marginalia.query.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.prometheus.client.Summary;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.index.api.QueryApiGrpc;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.query.QueryProtobufCodec;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.query.model.QueryResponse;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class QueryClient extends AbstractDynamicClient {

    private static final Summary wmsa_search_index_api_delegate_time = Summary.build().name("wmsa_search_index_api_delegate_time").help("-").register();
    private static final Summary wmsa_search_index_api_search_time = Summary.build().name("wmsa_search_index_api_search_time").help("-").register();

    private final Map<ServiceAndNode, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<ServiceAndNode, QueryApiGrpc.QueryApiBlockingStub > queryApis = new ConcurrentHashMap<>();

    record ServiceAndNode(String service, int node) {
        public String getHostName() {
            return service;
        }
    }
    private ManagedChannel getChannel(ServiceAndNode serviceAndNode) {
        return channels.computeIfAbsent(serviceAndNode,
                san -> ManagedChannelBuilder
                        .forAddress(serviceAndNode.getHostName(), 81)
                        .usePlaintext()
                        .build());
    }

    public QueryApiGrpc.QueryApiBlockingStub queryApi(int node) {
        return queryApis.computeIfAbsent(new ServiceAndNode("query-service", node), n ->
                QueryApiGrpc.newBlockingStub(
                        getChannel(n)
                )
        );
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public QueryClient(ServiceDescriptors descriptors) {

        super(descriptors.forId(ServiceId.Query), GsonFactory::get);
    }

    /** Delegate an Index API style query directly to the index service */
    @CheckReturnValue
    public SearchResultSet delegate(Context ctx, SearchSpecification specs) {
        return wmsa_search_index_api_delegate_time.time(
                () -> this.postGet(ctx, 0, "/delegate/", specs, SearchResultSet.class).blockingFirst()
        );
    }

    @CheckReturnValue
    public QueryResponse search(Context ctx, QueryParams params) {
        return QueryProtobufCodec.convertQueryResponse(queryApi(0).query(QueryProtobufCodec.convertQueryParams(params)));
    }

}
