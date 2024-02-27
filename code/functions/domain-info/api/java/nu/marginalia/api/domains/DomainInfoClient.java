package nu.marginalia.api.domains;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

import nu.marginalia.api.domains.model.*;

@Singleton
public class DomainInfoClient {
    private static final Logger logger = LoggerFactory.getLogger(DomainInfoClient.class);

    private final GrpcSingleNodeChannelPool<DomainInfoAPIGrpc.DomainInfoAPIBlockingStub> channelPool;
    private final ExecutorService executor = Executors.newWorkStealingPool(8);

    @Inject
    public DomainInfoClient(GrpcChannelPoolFactory factory) {
        this.channelPool = factory.createSingle(
                ServiceKey.forGrpcApi(DomainInfoAPIGrpc.class, ServicePartition.any()),
                DomainInfoAPIGrpc::newBlockingStub);
    }

    public Future<List<SimilarDomain>> similarDomains(int domainId, int count) {
        return channelPool.call(DomainInfoAPIGrpc.DomainInfoAPIBlockingStub::getSimilarDomains)
                .async(executor)
                .run(DomainsProtobufCodec.DomainQueries.createRequest(domainId, count))
                .thenApply(DomainsProtobufCodec.DomainQueries::convertResponse);
    }

    public Future<List<SimilarDomain>> linkedDomains(int domainId, int count) {
        return channelPool.call(DomainInfoAPIGrpc.DomainInfoAPIBlockingStub::getLinkingDomains)
                .async(executor)
                .run(DomainsProtobufCodec.DomainQueries.createRequest(domainId, count))
                .thenApply(DomainsProtobufCodec.DomainQueries::convertResponse);
    }

    public Future<DomainInformation> domainInformation(int domainId) {
        return channelPool.call(DomainInfoAPIGrpc.DomainInfoAPIBlockingStub::getDomainInfo)
                .async(executor)
                .run(DomainsProtobufCodec.DomainInfo.createRequest(domainId))
                .thenApply(DomainsProtobufCodec.DomainInfo::convertResponse);
    }

    public boolean isAccepting() {
        return channelPool.hasChannel();
    }
}
