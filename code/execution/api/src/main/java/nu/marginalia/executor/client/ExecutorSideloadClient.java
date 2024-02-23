package nu.marginalia.executor.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static nu.marginalia.functions.execution.api.ExecutorSideloadApiGrpc.ExecutorSideloadApiBlockingStub;

@Singleton
public class ExecutorSideloadClient {
    private final GrpcMultiNodeChannelPool<ExecutorSideloadApiBlockingStub> channelPool;
    private static final Logger logger = LoggerFactory.getLogger(ExecutorSideloadClient.class);

    @Inject
    public ExecutorSideloadClient(GrpcChannelPoolFactory grpcChannelPoolFactory)
    {
        this.channelPool = grpcChannelPoolFactory
                .createMulti(
                        ServiceKey.forGrpcApi(ExecutorSideloadApiGrpc.class, ServicePartition.multi()),
                        ExecutorSideloadApiGrpc::newBlockingStub);
    }


    public void sideloadEncyclopedia(int node, Path sourcePath, String baseUrl) {
        channelPool.call(ExecutorSideloadApiBlockingStub::sideloadEncyclopedia)
                .forNode(node)
                .run(RpcSideloadEncyclopedia.newBuilder()
                        .setBaseUrl(baseUrl)
                        .setSourcePath(sourcePath.toString())
                        .build());
    }

    public void sideloadDirtree(int node, Path sourcePath) {
        channelPool.call(ExecutorSideloadApiBlockingStub::sideloadDirtree)
                .forNode(node)
                .run(RpcSideloadDirtree.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build());
    }
    public void sideloadReddit(int node, Path sourcePath) {
        channelPool.call(ExecutorSideloadApiBlockingStub::sideloadReddit)
                .forNode(node)
                .run(RpcSideloadReddit.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build());
    }
    public void sideloadWarc(int node, Path sourcePath) {
        channelPool.call(ExecutorSideloadApiBlockingStub::sideloadWarc)
                .forNode(node)
                .run(RpcSideloadWarc.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build());
    }

    public void sideloadStackexchange(int node, Path sourcePath) {
        channelPool.call(ExecutorSideloadApiBlockingStub::sideloadStackexchange)
                .forNode(node)
                .run(RpcSideloadStackexchange.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build());
    }

}
