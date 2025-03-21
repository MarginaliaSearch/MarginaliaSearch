package nu.marginalia.api.favicon;

import com.google.inject.Inject;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class FaviconClient {
    private static final Logger logger = LoggerFactory.getLogger(FaviconClient.class);

    private final GrpcMultiNodeChannelPool<FaviconAPIGrpc.FaviconAPIBlockingStub> channelPool;

    @Inject
    public FaviconClient(GrpcChannelPoolFactory factory) {
        this.channelPool = factory.createMulti(
                ServiceKey.forGrpcApi(FaviconAPIGrpc.class, ServicePartition.multi()),
                FaviconAPIGrpc::newBlockingStub);
    }

    public record FaviconData(byte[] bytes, String contentType) {}


    public Optional<FaviconData> getFavicon(String domain, int node) {
        RpcFaviconResponse rsp = channelPool.call(FaviconAPIGrpc.FaviconAPIBlockingStub::getFavicon)
                .forNode(node)
                .run(RpcFaviconRequest.newBuilder().setDomain(domain).build());

        if (rsp.getData().isEmpty())
            return Optional.empty();

        return Optional.of(new FaviconData(rsp.getData().toByteArray(), rsp.getContentType()));
    }

}
