package nu.marginalia.executor.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.storage.model.FileStorageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static nu.marginalia.functions.execution.api.ExecutorExportApiGrpc.ExecutorExportApiBlockingStub;

@Singleton
public class ExecutorExportClient {
    private final GrpcMultiNodeChannelPool<ExecutorExportApiBlockingStub> channelPool;
    private static final Logger logger = LoggerFactory.getLogger(ExecutorExportClient.class);

    private final MqPersistence persistence;
    @Inject
    public ExecutorExportClient(GrpcChannelPoolFactory grpcChannelPoolFactory, MqPersistence persistence)
    {
        this.channelPool = grpcChannelPoolFactory
                .createMulti(
                        ServiceKey.forGrpcApi(ExecutorExportApiGrpc.class, ServicePartition.multi()),
                        ExecutorExportApiGrpc::newBlockingStub);
        this.persistence = persistence;
    }

    long createTrackingTokenMsg(String task, int node, Duration ttl) throws Exception {
        return persistence.sendNewMessage("task-tracking[" + node + "]", "export-client", null, task, "", ttl);
    }

    public long exportAtags(int node, FileStorageId fid) throws Exception {
        long msgId = createTrackingTokenMsg("atags", node, Duration.ofHours(6));

        channelPool.call(ExecutorExportApiBlockingStub::exportAtags)
                .forNode(node)
                .run(RpcExportRequest.newBuilder()
                        .setFileStorageId(fid.id())
                        .setMsgId(msgId)
                        .build());
        return msgId;
    }

    public void exportSampleData(int node, FileStorageId fid, int size, String ctFilter, String name) {
        channelPool.call(ExecutorExportApiBlockingStub::exportSampleData)
                .forNode(node)
                .run(RpcExportSampleData.newBuilder()
                        .setFileStorageId(fid.id())
                        .setSize(size)
                        .setCtFilter(ctFilter)
                        .setName(name)
                        .build());
    }

    public long exportRssFeeds(int node, FileStorageId fid) throws Exception {
        long msgId = createTrackingTokenMsg("rss", node, Duration.ofHours(6));
        channelPool.call(ExecutorExportApiBlockingStub::exportRssFeeds)
                .forNode(node)
                .run(RpcExportRequest.newBuilder()
                        .setFileStorageId(fid.id())
                        .setMsgId(msgId)
                        .build());
        return msgId;
    }

    public long exportTermFrequencies(int node, FileStorageId fid) throws Exception {
        long msgId = createTrackingTokenMsg("tfreq", node, Duration.ofHours(6));
        channelPool.call(ExecutorExportApiBlockingStub::exportTermFrequencies)
                .forNode(node)
                .run(RpcExportRequest.newBuilder()
                        .setFileStorageId(fid.id())
                        .setMsgId(msgId)
                        .build());
        return msgId;
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


    public void exportAllAtags() {
        channelPool.call(ExecutorExportApiBlockingStub::exportAllAtags)
                .forNode(1)
                .run(Empty.getDefaultInstance());
    }

    public void exportAllFeeds() {
        channelPool.call(ExecutorExportApiBlockingStub::exportAllFeeds)
                .forNode(1)
                .run(Empty.getDefaultInstance());
    }

    public void exportAllTfreqs() {
        channelPool.call(ExecutorExportApiBlockingStub::exportAllTfreqs)
                .forNode(1)
                .run(Empty.getDefaultInstance());
    }
}
