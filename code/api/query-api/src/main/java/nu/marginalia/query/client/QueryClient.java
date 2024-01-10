package nu.marginalia.query.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.array.TIntArrayList;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.prometheus.client.Summary;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.index.api.Empty;
import nu.marginalia.index.api.IndexDomainLinksApiGrpc;
import nu.marginalia.index.api.QueryApiGrpc;
import nu.marginalia.index.api.RpcDomainId;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.query.QueryProtobufCodec;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.query.model.QueryResponse;
import nu.marginalia.service.descriptor.ServiceDescriptor;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import org.roaringbitmap.PeekableCharIterator;
import org.roaringbitmap.longlong.PeekableLongIterator;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class QueryClient extends AbstractDynamicClient {

    private static final Summary wmsa_qs_api_delegate_time = Summary.build()
            .name("wmsa_qs_api_delegate_time")
            .help("query service delegate time")
            .register();
    private static final Summary wmsa_qs_api_search_time = Summary.build()
            .name("wmsa_qs_api_search_time")
            .help("query service search time")
            .register();

    private final Map<ServiceAndNode, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<ServiceAndNode, QueryApiGrpc.QueryApiBlockingStub > queryIndexApis = new ConcurrentHashMap<>();
    private final Map<ServiceAndNode, IndexDomainLinksApiGrpc.IndexDomainLinksApiBlockingStub> domainLinkApis = new ConcurrentHashMap<>();

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
        return queryIndexApis.computeIfAbsent(new ServiceAndNode("query-service", node), n ->
                QueryApiGrpc.newBlockingStub(
                        getChannel(n)
                )
        );
    }

    public IndexDomainLinksApiGrpc.IndexDomainLinksApiBlockingStub domainApi(int node) {
        return domainLinkApis.computeIfAbsent(new ServiceAndNode("query-service", node), n ->
                IndexDomainLinksApiGrpc.newBlockingStub(
                        getChannel(n)
                )
        );
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public QueryClient(ServiceDescriptors descriptors) {

        super(descriptors.forId(ServiceId.Query), GsonFactory::get);
    }
    public QueryClient() {
        super(new ServiceDescriptor(ServiceId.Query, "query-service"), GsonFactory::get);
    }

    /** Delegate an Index API style query directly to the index service */
    @CheckReturnValue
    public SearchResultSet delegate(Context ctx, SearchSpecification specs) {
        return wmsa_qs_api_delegate_time.time(
                () -> this.postGet(ctx, 0, "/delegate/", specs, SearchResultSet.class).blockingFirst()
        );
    }

    @CheckReturnValue
    public QueryResponse search(Context ctx, QueryParams params) {
        return wmsa_qs_api_search_time.time(
                () ->  QueryProtobufCodec.convertQueryResponse(queryApi(0).query(QueryProtobufCodec.convertQueryParams(params)))
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
            return domainApi(0).getLinksToDomain(RpcDomainId
                            .newBuilder()
                            .setDomainId(domainId)
                            .build())
                    .getDomainIdList();
        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return List.of();
        }
    }

    public List<Integer> getLinksFromDomain(int domainId) {
        try {
            return domainApi(0).getLinksFromDomain(RpcDomainId
                            .newBuilder()
                            .setDomainId(domainId)
                            .build())
                    .getDomainIdList();
        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return List.of();
        }
    }

    public int countLinksToDomain(int domainId) {
        try {
            return domainApi(0).countLinksToDomain(RpcDomainId
                            .newBuilder()
                            .setDomainId(domainId)
                            .build())
                    .getIdCount();
        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return 0;
        }
    }

    public int countLinksFromDomain(int domainId) {
        try {
            return domainApi(0).countLinksFromDomain(RpcDomainId
                            .newBuilder()
                            .setDomainId(domainId)
                            .build())
                    .getIdCount();
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
