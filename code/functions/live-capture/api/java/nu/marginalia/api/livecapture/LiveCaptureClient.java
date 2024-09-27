package nu.marginalia.api.livecapture;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.livecapture.LiveCaptureApiGrpc.LiveCaptureApiBlockingStub;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LiveCaptureClient {
    private static final Logger logger = LoggerFactory.getLogger(LiveCaptureClient.class);

    private final GrpcSingleNodeChannelPool<LiveCaptureApiBlockingStub> channelPool;

    @Inject
    public LiveCaptureClient(GrpcChannelPoolFactory factory) {
        // The client is only interested in the primary node
        var key = ServiceKey.forGrpcApi(LiveCaptureApiGrpc.class, ServicePartition.any());
        this.channelPool = factory.createSingle(key, LiveCaptureApiGrpc::newBlockingStub);
    }


    public void requestScreengrab(int domainId) {
        try {
            channelPool.call(LiveCaptureApiBlockingStub::requestScreengrab)
                    .run(RpcDomainId.newBuilder().setDomainId(domainId).build());
        }
        catch (Exception e) {
            logger.error("API Exception", e);
        }
    }
}
