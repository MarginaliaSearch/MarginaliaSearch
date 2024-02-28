package nu.marginalia.api.linkgraph;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PartitionLinkGraphClient {
    private static final Logger logger = LoggerFactory.getLogger(PartitionLinkGraphClient.class);

    private final GrpcMultiNodeChannelPool<LinkGraphApiGrpc.LinkGraphApiBlockingStub> channelPool;

    @Inject
    public PartitionLinkGraphClient(GrpcChannelPoolFactory factory) {
        this.channelPool = factory.createMulti(
                ServiceKey.forGrpcApi(LinkGraphApiGrpc.class, ServicePartition.multi()),
                LinkGraphApiGrpc::newBlockingStub);
    }

    public GrpcMultiNodeChannelPool<LinkGraphApiGrpc.LinkGraphApiBlockingStub> getChannelPool() {
        return channelPool;
    }

}
