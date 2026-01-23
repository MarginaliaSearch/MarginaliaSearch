package nu.marginalia.api.linkgraph;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.roaringbitmap.longlong.PeekableLongIterator;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static nu.marginalia.api.linkgraph.LinkGraphApiGrpc.*;

@Singleton
public class AggregateLinkGraphClient {
    private static final Logger logger = LoggerFactory.getLogger(AggregateLinkGraphClient.class);

    private final GrpcSingleNodeChannelPool<LinkGraphApiBlockingStub> channelPool;

    @Inject
    public AggregateLinkGraphClient(GrpcChannelPoolFactory factory) {
        this.channelPool = factory.createSingle(
                ServiceKey.forGrpcApi(LinkGraphApiGrpc.class, ServicePartition.any()),
                LinkGraphApiGrpc::newBlockingStub);
    }


    public AllLinks getAllDomainLinks() {
        AllLinks links = new AllLinks();

        channelPool.call(LinkGraphApiBlockingStub::getAllLinks)
                .run(Empty.getDefaultInstance())
                .forEachRemaining(pairs -> {
                    for (int i = 0; i < pairs.getDestIdsCount(); i++) {
                        links.add(pairs.getSourceIds(i), pairs.getDestIds(i));
                    }
                });

        return links;
    }

    public List<Integer> getLinksToDomain(int domainId) {
        try {
            return channelPool.call(LinkGraphApiBlockingStub::getLinksToDomain)
                    .run(RpcDomainId.newBuilder().setDomainId(domainId).build())
                    .getDomainIdList()
                    .stream()
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
            return channelPool.call(LinkGraphApiBlockingStub::getLinksFromDomain)
                    .run(RpcDomainId.newBuilder().setDomainId(domainId).build())
                    .getDomainIdList()
                    .stream()
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
            return channelPool.call(LinkGraphApiBlockingStub::countLinksToDomain)
                    .run(RpcDomainId.newBuilder().setDomainId(domainId).build())
                    .getIdCount();

        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return 0;
        }
    }

    public int countLinksFromDomain(int domainId) {
        try {
            return channelPool.call(LinkGraphApiBlockingStub::countLinksFromDomain)
                    .run(RpcDomainId.newBuilder().setDomainId(domainId).build())
                    .getIdCount();
        }
        catch (Exception e) {
            logger.error("API Exception", e);
            return 0;
        }
    }

    public boolean waitReady(Duration duration) throws InterruptedException {
        return channelPool.awaitChannel(duration);
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
