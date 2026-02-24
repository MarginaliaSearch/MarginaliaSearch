package nu.marginalia.api.searchquery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.prometheus.metrics.core.metrics.Summary;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.service.client.GrpcChannelPoolFactoryIf;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.Initialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

@Singleton
public class QueryClient  {

    private static final Logger log = LoggerFactory.getLogger(QueryClient.class);
    private final ExecutorService virtualThreadService = Executors.newVirtualThreadPerTaskExecutor();

    private static final Summary wmsa_qs_api_search_time = Summary.builder()
            .name("wmsa_qs_api_search_time")
            .help("query service search time")
            .register();

    private final GrpcSingleNodeChannelPool<QueryApiGrpc.QueryApiBlockingStub> queryApiPool;

    @Inject
    public QueryClient(GrpcChannelPoolFactoryIf channelPoolFactory,
                       Initialization initialization
                       ) throws InterruptedException
    {
        this.queryApiPool = channelPoolFactory.createSingle(
                ServiceKey.forGrpcApi(QueryApiGrpc.class, ServicePartition.any()),
                QueryApiGrpc::newBlockingStub);

        initialization.addCallback(() -> {
            // Hold up initialization until we have a downstream connection
            try {
                this.queryApiPool.awaitChannel(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @CheckReturnValue
    public QueryResponse search(QueryFilterSpec filterSpec,
                                String humanQuery,
                                String languageIsoCode,
                                NsfwFilterTier filterTier,
                                RpcQueryLimits limits,
                                int page) throws TimeoutException
    {
        RpcQsQuery.Builder queryBuilder = RpcQsQuery.newBuilder();

        filterSpec.configure(queryBuilder);

        var query = queryBuilder.setHumanQuery(humanQuery)
                .setLangIsoCode(languageIsoCode)
                .setNsfwFilterTierValue(filterTier.getCodedValue())
                .setQueryLimits(limits)
                .setPagination(
                        RpcQsQueryPagination.newBuilder()
                                .setPage(page)
                                .setPageSize(Math.min(100, limits.getResultsTotal()))
                                .build()
                )
                .build();

        try (var _ = wmsa_qs_api_search_time.startTimer()) {
            RpcQsResponse rsp = queryApiPool.call(
                    channel -> QueryApiGrpc.newBlockingStub(channel)
                                        .withDeadlineAfter(Duration.ofMillis(limits.getTimeoutMs() * 2)),
                                    QueryApiGrpc.QueryApiBlockingStub::query,
                                    query);

            return QueryProtobufCodec.convertQueryResponse(rsp);
        }
    }

    public boolean invalidateFilterCache(String userId, String filterId) {
        List<Future<Empty>> ret = queryApiPool.call(QueryApiGrpc.QueryApiBlockingStub::invalidateFilterCache)
                .broadcast()
                .run(
                    RpcQsInvalidateFilter.newBuilder()
                        .setUserId(userId)
                        .setFilterId(filterId)
                        .build()
                );

        for (var fut : ret) {
            if (fut.state() != Future.State.SUCCESS)
                return false;
        }

        return true;
    }

}
