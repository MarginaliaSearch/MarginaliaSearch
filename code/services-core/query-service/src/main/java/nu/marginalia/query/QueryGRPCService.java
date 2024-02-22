package nu.marginalia.query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.index.api.*;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.query.svc.QueryFactory;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

@Singleton
public class QueryGRPCService extends QueryApiGrpc.QueryApiImplBase {

    private final Logger logger = LoggerFactory.getLogger(QueryGRPCService.class);

    private static final Histogram wmsa_qs_query_time_grpc = Histogram.build()
            .name("wmsa_qs_query_time_grpc")
            .labelNames("timeout", "count")
            .linearBuckets(0.05, 0.05, 15)
            .help("QS-side query time (GRPC endpoint)")
            .register();

    private final GrpcMultiNodeChannelPool<IndexApiGrpc.IndexApiBlockingStub> channelPool;

    private final QueryFactory queryFactory;
    private final DomainBlacklist blacklist;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Inject
    public QueryGRPCService(QueryFactory queryFactory,
                            GrpcChannelPoolFactory channelPoolFactory,
                            DomainBlacklist blacklist)
    {
        this.queryFactory = queryFactory;
        this.blacklist = blacklist;
        this.channelPool = channelPoolFactory.createMulti(
                ServiceKey.forGrpcApi(IndexApiGrpc.class, ServicePartition.multi()),
                IndexApiGrpc::newBlockingStub);
    }

    public void query(nu.marginalia.index.api.RpcQsQuery request,
                      io.grpc.stub.StreamObserver<nu.marginalia.index.api.RpcQsResponse> responseObserver)
    {
        try {
            wmsa_qs_query_time_grpc
                    .labels(Integer.toString(request.getQueryLimits().getTimeoutMs()),
                            Integer.toString(request.getQueryLimits().getResultsTotal()))
                    .time(() -> {
                var params = QueryProtobufCodec.convertRequest(request);
                var query = queryFactory.createQuery(params);

                RpcIndexQuery indexRequest = QueryProtobufCodec.convertQuery(request, query);
                List<RpcDecoratedResultItem> bestItems = executeQueries(indexRequest, request.getQueryLimits().getResultsTotal());

                var responseBuilder = RpcQsResponse.newBuilder()
                        .addAllResults(bestItems)
                        .setSpecs(indexRequest)
                        .addAllSearchTermsHuman(query.searchTermsHuman);

                if (query.domain != null)
                    responseBuilder.setDomain(query.domain);

                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
            });
        } catch (Exception e) {
            logger.error("Exception", e);
            responseObserver.onError(e);
        }
    }

    private static final Comparator<RpcDecoratedResultItem> comparator =
            Comparator.comparing(RpcDecoratedResultItem::getRankingScore);

    @SneakyThrows
    List<RpcDecoratedResultItem> executeQueries(RpcIndexQuery indexRequest, int totalSize) {
        var futures =
            channelPool.call(IndexApiGrpc.IndexApiBlockingStub::query)
                .async(executor)
                .runEach(indexRequest);
        List<RpcDecoratedResultItem> results = new ArrayList<>();
        for (var future : futures) {
            try {
                future.get().forEachRemaining(results::add);
            }
            catch (Exception e) {
                logger.error("Downstream exception", e);
            }
        }
        results.sort(comparator);
        results.removeIf(this::isBlacklisted);
        return results.subList(0, Math.min(totalSize, results.size()));
    }

    private boolean isBlacklisted(RpcDecoratedResultItem item) {
        return blacklist.isBlacklisted(UrlIdCodec.getDomainId(item.getRawItem().getCombinedId()));
    }
}
