package nu.marginalia.query;

import com.google.inject.Inject;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.index.api.*;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.query.svc.NodeConfigurationWatcher;
import nu.marginalia.query.svc.QueryFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class QueryGRPCService extends QueryApiGrpc.QueryApiImplBase {

    private final Logger logger = LoggerFactory.getLogger(QueryGRPCService.class);

    private static final Histogram wmsa_qs_query_time_grpc = Histogram.build()
            .name("wmsa_qs_query_time_grpc")
            .labelNames("timeout", "count")
            .linearBuckets(0.05, 0.05, 15)
            .help("QS-side query time (GRPC endpoint)")
            .register();

    private final Map<ServiceAndNode, ManagedChannel> channels
            = new ConcurrentHashMap<>();
    private final Map<ServiceAndNode, IndexApiGrpc.IndexApiBlockingStub> actorRpcApis
            = new ConcurrentHashMap<>();

    private ManagedChannel getChannel(ServiceAndNode serviceAndNode) {
        return channels.computeIfAbsent(serviceAndNode,
                san -> ManagedChannelBuilder
                        .forAddress(serviceAndNode.getHostName(), 81)
                        .usePlaintext()
                        .build());
    }

    public IndexApiGrpc.IndexApiBlockingStub indexApi(int node) {
        return actorRpcApis.computeIfAbsent(new ServiceAndNode("index-service", node), n ->
                IndexApiGrpc.newBlockingStub(
                        getChannel(n)
                )
        );
    }

    record ServiceAndNode(String service, int node) {
        public String getHostName() {
            return service+"-"+node;
        }
    }

    private final QueryFactory queryFactory;
    private final DomainBlacklist blacklist;
    private final NodeConfigurationWatcher nodeConfigurationWatcher;

    @Inject
    public QueryGRPCService(QueryFactory queryFactory, DomainBlacklist blacklist, NodeConfigurationWatcher nodeConfigurationWatcher) {
        this.queryFactory = queryFactory;
        this.blacklist = blacklist;
        this.nodeConfigurationWatcher = nodeConfigurationWatcher;
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

    private final ExecutorService es = Executors.newVirtualThreadPerTaskExecutor();

    private static final Comparator<RpcDecoratedResultItem> comparator =
            Comparator.comparing(RpcDecoratedResultItem::getRankingScore);

    @SneakyThrows
    private List<RpcDecoratedResultItem> executeQueries(RpcIndexQuery indexRequest, int totalSize) {
        List<Callable<List<RpcDecoratedResultItem>>> tasks = createTasks(indexRequest);

        return es.invokeAll(tasks).stream()
                .filter(f -> f.state() == Future.State.SUCCESS)
                .map(Future::resultNow)
                .flatMap(List::stream)
                .sorted(comparator)
                .limit(totalSize)
                .toList();
    }

    @NotNull
    private List<Callable<List<RpcDecoratedResultItem>>> createTasks(RpcIndexQuery indexRequest) {
        List<Callable<List<RpcDecoratedResultItem>>> tasks = new ArrayList<>();

        for (var node : nodeConfigurationWatcher.getQueryNodes()) {
            tasks.add(() -> {
                var responseIter = indexApi(node).query(indexRequest);
                var ret = new ArrayList<RpcDecoratedResultItem>();
                while (responseIter.hasNext()) {
                    RpcDecoratedResultItem next = responseIter.next();
                    if (isBlacklisted(next))
                        continue;
                    ret.add(next);
                }
                return ret;
            });
        }
        return tasks;
    }


    private boolean isBlacklisted(RpcDecoratedResultItem item) {
        return blacklist.isBlacklisted(UrlIdCodec.getDomainId(item.getRawItem().getCombinedId()));
    }
}
