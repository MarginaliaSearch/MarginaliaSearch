package nu.marginalia.executor.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.storage.model.FileStorageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static nu.marginalia.functions.execution.api.ExecutorExportApiGrpc.ExecutorExportApiBlockingStub;

@Singleton
public class ExecutorExportClient {
    private final GrpcMultiNodeChannelPool<ExecutorExportApiBlockingStub> channelPool;
    private static final Logger logger = LoggerFactory.getLogger(ExecutorExportClient.class);

    @Inject
    public ExecutorExportClient(GrpcChannelPoolFactory grpcChannelPoolFactory)
    {
        this.channelPool = grpcChannelPoolFactory
                .createMulti(
                        ServiceKey.forGrpcApi(ExecutorExportApiGrpc.class, ServicePartition.multi()),
                        ExecutorExportApiGrpc::newBlockingStub);
    }


    public void exportAtags(int node, FileStorageId fid) {
        channelPool.call(ExecutorExportApiBlockingStub::exportAtags)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }
    public void exportSampleData(int node, FileStorageId fid, int size, String name) {
        channelPool.call(ExecutorExportApiBlockingStub::exportSampleData)
                .forNode(node)
                .run(RpcExportSampleData.newBuilder()
                        .setFileStorageId(fid.id())
                        .setSize(size)
                        .setName(name)
                        .build());
    }

    public void exportRssFeeds(int node, FileStorageId fid) {
        channelPool.call(ExecutorExportApiBlockingStub::exportRssFeeds)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }

    public void exportTermFrequencies(int node, FileStorageId fid) {
        channelPool.call(ExecutorExportApiBlockingStub::exportTermFrequencies)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }

    public void exportData(int node) {
        channelPool.call(ExecutorExportApiBlockingStub::exportData)
                .forNode(node)
                .run(Empty.getDefaultInstance());
    }

    public void exportSegmentationModel(int node, String path) {
        channelPool.call(ExecutorExportApiBlockingStub::exportSegmentationModel)
                .forNode(node)
                .run(RpcExportSegmentationModel
                        .newBuilder()
                        .setSourcePath(path)
                        .build());
    }


}
