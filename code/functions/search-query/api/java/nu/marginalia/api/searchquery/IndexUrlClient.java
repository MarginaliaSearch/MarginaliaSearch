package nu.marginalia.api.searchquery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.StatusRuntimeException;
import nu.marginalia.service.client.GrpcChannelPoolFactoryIf;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.Initialization;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Singleton
public class IndexUrlClient {

    private static final Logger log = LoggerFactory.getLogger(IndexUrlClient.class);

    private final GrpcMultiNodeChannelPool<IndexUrlApiGrpc.IndexUrlApiBlockingStub> indexApiPool;

    @Inject
    public IndexUrlClient(GrpcChannelPoolFactoryIf channelPoolFactory, Initialization initialization) throws InterruptedException
    {
        this.indexApiPool = channelPoolFactory.createMulti(
                ServiceKey.forGrpcApi(IndexUrlApiGrpc.class, ServicePartition.multi()),
                IndexUrlApiGrpc::newBlockingStub);
    }

    public Optional<String> getUrl(int node, long docId) throws TimeoutException {
        try {
            return Optional.ofNullable(indexApiPool.call(IndexUrlApiGrpc.IndexUrlApiBlockingStub::getUrl)
                    .forNode(node)
                    .run(RpcDocumentIdRequest.newBuilder().setDocId(docId).build())
                    .getUrl())
                    .filter(Strings::isNotBlank);
        }
        catch (StatusRuntimeException ex) {
            log.warn("Failed to get URL for document {}:{}", node, docId, ex);

            return Optional.empty();
        }
    }

}
