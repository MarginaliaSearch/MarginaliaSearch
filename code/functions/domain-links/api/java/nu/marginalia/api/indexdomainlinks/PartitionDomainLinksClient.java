package nu.marginalia.api.indexdomainlinks;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.domainlink.DomainLinksApiGrpc;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PartitionDomainLinksClient {
    private static final Logger logger = LoggerFactory.getLogger(PartitionDomainLinksClient.class);

    private final GrpcMultiNodeChannelPool<DomainLinksApiGrpc.DomainLinksApiBlockingStub> channelPool;

    @Inject
    public PartitionDomainLinksClient(GrpcChannelPoolFactory factory) {
        this.channelPool = factory.createMulti(
                ServiceKey.forGrpcApi(DomainLinksApiGrpc.class, ServicePartition.multi()),
                DomainLinksApiGrpc::newBlockingStub);
    }

    public GrpcMultiNodeChannelPool<DomainLinksApiGrpc.DomainLinksApiBlockingStub> getChannelPool() {
        return channelPool;
    }

}
