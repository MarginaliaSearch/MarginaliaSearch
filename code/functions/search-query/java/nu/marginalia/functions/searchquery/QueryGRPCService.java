package nu.marginalia.functions.searchquery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.functions.searchquery.svc.QueryFactory;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.model.id.UrlIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Singleton
public class QueryGRPCService extends QueryApiGrpc.QueryApiImplBase {

    private final Logger logger = LoggerFactory.getLogger(QueryGRPCService.class);

    private static final Histogram wmsa_qs_query_time_grpc = Histogram.build()
            .name("wmsa_qs_query_time_grpc")
            .labelNames("timeout", "count")
            .linearBuckets(0.05, 0.05, 15)
            .help("QS-side query time (GRPC endpoint)")
            .register();


    private final QueryFactory queryFactory;
    private final DomainBlacklist blacklist;
    private final IndexClient indexClient;
    @Inject
    public QueryGRPCService(QueryFactory queryFactory,
                            DomainBlacklist blacklist,
                            IndexClient indexClient)
    {
        this.queryFactory = queryFactory;
        this.blacklist = blacklist;
        this.indexClient = indexClient;
    }

    public void query(RpcQsQuery request, StreamObserver<RpcQsResponse> responseObserver)
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


    private boolean isBlacklisted(RpcDecoratedResultItem item) {
        return blacklist.isBlacklisted(UrlIdCodec.getDomainId(item.getRawItem().getCombinedId()));
    }

    public List<DecoratedSearchResultItem> executeDirect(String originalQuery, QueryParams params, int count) {
        var query = queryFactory.createQuery(params);

        return executeQueries(
                QueryProtobufCodec.convertQuery(originalQuery, query),
                count)
                .stream().map(QueryProtobufCodec::convertQueryResult)
                .toList();
    }

    @SneakyThrows
    List<RpcDecoratedResultItem> executeQueries(RpcIndexQuery indexRequest, int totalSize) {
        var results = indexClient.executeQueries(indexRequest);

        results.sort(comparator);
        results.removeIf(this::isBlacklisted);
        return results.subList(0, Math.min(totalSize, results.size()));
    }

}
