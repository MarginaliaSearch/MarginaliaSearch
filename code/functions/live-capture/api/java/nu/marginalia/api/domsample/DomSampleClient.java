package nu.marginalia.api.domsample;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Singleton
public class DomSampleClient {
    private final GrpcSingleNodeChannelPool<DomSampleApiGrpc.DomSampleApiBlockingStub> channelPool;
    private static final Logger logger = LoggerFactory.getLogger(DomSampleClient.class);

    @Inject
    public DomSampleClient(GrpcChannelPoolFactory factory) {

        // The client is only interested in the primary node
        var key = ServiceKey.forGrpcApi(DomSampleApiGrpc.class, ServicePartition.any());
        this.channelPool = factory.createSingle(key, DomSampleApiGrpc::newBlockingStub);
    }

    public Optional<RpcDomainSample> getSample(String domainName) {
        try {
            var val = channelPool.call(DomSampleApiGrpc.DomSampleApiBlockingStub::getSample)
                    .run(RpcDomainName.newBuilder().setDomainName(domainName).build());

            return Optional.of(val);
        }
        catch (StatusRuntimeException sre) {
            if (sre.getStatus() != Status.NOT_FOUND) {
                logger.error("Failed to fetch DOM sample");
            }
            return Optional.empty();
        }
    }

    public CompletableFuture<RpcDomainSample> getSampleAsync(String domainName, ExecutorService executorService) {
        return channelPool.call(DomSampleApiGrpc.DomSampleApiBlockingStub::getSample)
                .async(executorService)
                .run(RpcDomainName.newBuilder().setDomainName(domainName).build());
    }

    public List<RpcDomainSample> getAllSamples(String domainName) {
        try {
            Iterator<RpcDomainSample> val = channelPool.call(DomSampleApiGrpc.DomSampleApiBlockingStub::getAllSamples)
                    .run(RpcDomainName.newBuilder().setDomainName(domainName).build());

            List<RpcDomainSample> ret = new ArrayList<>();
            val.forEachRemaining(ret::add);
            return ret;
        }
        catch (StatusRuntimeException sre) {
            logger.error("Failed to fetch DOM sample");
            return List.of();
        }
    }

    public boolean waitReady(Duration duration) throws InterruptedException {
        return channelPool.awaitChannel(duration);
    }


}
