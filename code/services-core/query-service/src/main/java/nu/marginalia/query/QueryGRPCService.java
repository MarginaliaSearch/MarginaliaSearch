package nu.marginalia.query;

import com.google.inject.Inject;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.index.api.*;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.query.svc.NodeConfigurationWatcher;
import nu.marginalia.query.svc.QueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class QueryGRPCService extends QueryApiGrpc.QueryApiImplBase {

    private final Logger logger = LoggerFactory.getLogger(QueryGRPCService.class);

    private final Map<ServiceAndNode, ManagedChannel> channels
            = new ConcurrentHashMap<>();
    private final Map<ServiceAndNode, IndexApiGrpc.IndexApiFutureStub> actorRpcApis
            = new ConcurrentHashMap<>();

    private ManagedChannel getChannel(ServiceAndNode serviceAndNode) {
        return channels.computeIfAbsent(serviceAndNode,
                san -> ManagedChannelBuilder
                        .forAddress(serviceAndNode.getHostName(), 81)
                        .usePlaintext()
                        .build());
    }

    public IndexApiGrpc.IndexApiFutureStub indexApi(int node) {
        return actorRpcApis.computeIfAbsent(new ServiceAndNode("index-service", node), n ->
                IndexApiGrpc.newFutureStub(
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
        } catch (Exception e) {
            logger.error("Exception", e);
            responseObserver.onError(e);
        }
    }

    private List<RpcDecoratedResultItem> executeQueries(RpcIndexQuery indexRequest, int totalSize) throws InterruptedException
    {

        final List<RpcDecoratedResultItem> bestItems = new ArrayList<>(2 * totalSize);

        LinkedList<Future<RpcSearchResultSet>> resultSets = new LinkedList<>();
        for (var node : nodeConfigurationWatcher.getQueryNodes()) {
            resultSets.add(indexApi(node).query(indexRequest));
        }

        long start = System.currentTimeMillis();
        long timeout = start + 500;

        while (!resultSets.isEmpty() && System.currentTimeMillis() < timeout)
        {
            resultSets.removeIf(f -> switch(f.state()) {
                case CANCELLED -> true;
                case FAILED -> {
                    logger.error("Error in query", f.exceptionNow());
                    yield true;
                }
                case SUCCESS -> {
                    mergeResults(bestItems, f.resultNow(), totalSize);
                    yield true;
                }
                case RUNNING -> false;
            });

            if (!resultSets.isEmpty()) {
                // yield
                TimeUnit.MILLISECONDS.sleep(1);
            }
        }
        return bestItems;
    }

    private static final Comparator<RpcDecoratedResultItem> comparator =
            Comparator.comparing(RpcDecoratedResultItem::getRankingScore);
    private void mergeResults(List<RpcDecoratedResultItem> bestItems,
                              RpcSearchResultSet result,
                              int totalSize)
    {
        for (int i = 0; i < result.getItemsCount(); i++) {
            var item = result.getItems(i);
            if (isBlacklisted(item)) {
                continue;
            }
            bestItems.add(result.getItems(i));
        }

        bestItems.sort(comparator);

        if (bestItems.size() > totalSize) {
            bestItems.subList(totalSize, bestItems.size()).clear();
        }
    }

    private boolean isBlacklisted(RpcDecoratedResultItem item) {
        return blacklist.isBlacklisted(UrlIdCodec.getDomainId(item.getRawItem().getCombinedId()));
    }
}
