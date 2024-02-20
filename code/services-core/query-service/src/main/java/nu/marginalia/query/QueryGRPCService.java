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
import nu.marginalia.service.id.ServiceId;
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

    @Inject
    public QueryGRPCService(QueryFactory queryFactory,
                            GrpcChannelPoolFactory channelPoolFactory,
                            DomainBlacklist blacklist)
    {
        this.queryFactory = queryFactory;
        this.blacklist = blacklist;
        this.channelPool = channelPoolFactory.createMulti(ServiceId.Index, IndexApiGrpc::newBlockingStub);
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
        return channelPool.invokeAll(stub -> new QueryTask(stub, indexRequest))
                .stream()
                .filter(f -> f.state() == Future.State.SUCCESS)
                .map(Future::resultNow)
                .flatMap(List::stream)
                .sorted(comparator)
                .limit(totalSize)
                .toList();
    }

    private class QueryTask implements Callable<List<RpcDecoratedResultItem>> {
        private final IndexApiGrpc.IndexApiBlockingStub stub;
        private final RpcIndexQuery indexRequest;

        public QueryTask(IndexApiGrpc.IndexApiBlockingStub stub, RpcIndexQuery indexRequest) {
            this.stub = stub;
            this.indexRequest = indexRequest;
        }

        @Override
        public List<RpcDecoratedResultItem> call() {
            var rsp = stub.query(indexRequest);
            List<RpcDecoratedResultItem> ret = new ArrayList<>();

            while (rsp.hasNext()) {
                RpcDecoratedResultItem next = rsp.next();
                if (isBlacklisted(next))
                    continue;
                ret.add(next);
            }

            return ret;
        }
    }

    private boolean isBlacklisted(RpcDecoratedResultItem item) {
        return blacklist.isBlacklisted(UrlIdCodec.getDomainId(item.getRawItem().getCombinedId()));
    }
}
