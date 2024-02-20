package nu.marginalia.query.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Summary;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.index.api.Empty;
import nu.marginalia.index.api.IndexDomainLinksApiGrpc;
import nu.marginalia.index.api.QueryApiGrpc;
import nu.marginalia.index.api.RpcDomainId;
import nu.marginalia.query.QueryProtobufCodec;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.query.model.QueryResponse;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.id.ServiceId;
import org.roaringbitmap.longlong.PeekableLongIterator;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.List;

@Singleton
public class QueryClient  {

    private static final Summary wmsa_qs_api_search_time = Summary.build()
            .name("wmsa_qs_api_search_time")
            .help("query service search time")
            .register();

    private final GrpcSingleNodeChannelPool<QueryApiGrpc.QueryApiBlockingStub> queryApiPool;
    private final GrpcMultiNodeChannelPool<IndexDomainLinksApiGrpc.IndexDomainLinksApiBlockingStub> domainLinkApiPool;

    public IndexDomainLinksApiGrpc.IndexDomainLinksApiBlockingStub domainApi(int node) {
        return domainLinkApiPool.apiForNode(node);
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public QueryClient(GrpcChannelPoolFactory channelPoolFactory) {
        this.queryApiPool = channelPoolFactory.createSingle(ServiceId.Query, QueryApiGrpc::newBlockingStub);
        this.domainLinkApiPool = channelPoolFactory.createMulti(ServiceId.Index, IndexDomainLinksApiGrpc::newBlockingStub);
    }

    @CheckReturnValue
    public QueryResponse search(QueryParams params) {
        var query = QueryProtobufCodec.convertQueryParams(params);

        return wmsa_qs_api_search_time.time(
                () ->  QueryProtobufCodec.convertQueryResponse(queryApiPool
                        .importantCall((api) -> api.query(query))
                )
        );
    }

    public AllLinks getAllDomainLinks() {
        AllLinks links = new AllLinks();

        domainApi(0).getAllLinks(Empty.newBuilder().build()).forEachRemaining(pairs -> {
            for (int i = 0; i < pairs.getDestIdsCount(); i++) {
                links.add(pairs.getSourceIds(i), pairs.getDestIds(i));
            }
        });

        return links;
    }

    public List<Integer> getLinksToDomain(int domainId) {
        try {
            return domainLinkApiPool.callEachSequential(
                    api -> api.getLinksToDomain(RpcDomainId
                            .newBuilder()
                            .setDomainId(domainId)
                            .build())
                    .getDomainIdList())
                    .flatMap(List::stream)
                    .sorted()
                    .toList();

        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return List.of();
        }
    }

    public List<Integer> getLinksFromDomain(int domainId) {
        try {
            return domainLinkApiPool.callEachSequential(
                            api -> api.getLinksFromDomain(RpcDomainId
                                            .newBuilder()
                                            .setDomainId(domainId)
                                            .build())
                                    .getDomainIdList())
                    .flatMap(List::stream)
                    .sorted()
                    .toList();
        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return List.of();
        }
    }

    public int countLinksToDomain(int domainId) {
        try {
            return domainLinkApiPool.callEachSequential(
                            api -> api.countLinksToDomain(RpcDomainId
                                            .newBuilder()
                                            .setDomainId(domainId)
                                            .build()).getIdCount())
                    .mapToInt(Integer::valueOf)
                    .sum();
        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return 0;
        }
    }

    public int countLinksFromDomain(int domainId) {
        try {
            return domainLinkApiPool.callEachSequential(
                            api -> api.countLinksFromDomain(RpcDomainId
                                    .newBuilder()
                                    .setDomainId(domainId)
                                    .build()).getIdCount())
                    .mapToInt(Integer::valueOf)
                    .sum();
        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return 0;
        }
    }
    public static class AllLinks {
        private final Roaring64Bitmap sourceToDest = new Roaring64Bitmap();

        public void add(int source, int dest) {
            sourceToDest.add(Integer.toUnsignedLong(source) << 32 | Integer.toUnsignedLong(dest));
        }

        public Iterator iterator() {
            return new Iterator();
        }

        public class Iterator {
            private final PeekableLongIterator base = sourceToDest.getLongIterator();
            long val = Long.MIN_VALUE;

            public boolean advance() {
                if (base.hasNext()) {
                    val = base.next();
                    return true;
                }
                return false;
            }
            public int source() {
                return (int) (val >>> 32);
            }
            public int dest() {
                return (int) (val & 0xFFFF_FFFFL);
            }
        }
    }
}
