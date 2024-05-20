package nu.marginalia.executor.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.executor.model.ActorRunState;
import nu.marginalia.executor.model.ActorRunStates;
import nu.marginalia.executor.storage.FileStorageContent;
import nu.marginalia.executor.storage.FileStorageFile;
import nu.marginalia.executor.upload.UploadDirContents;
import nu.marginalia.executor.upload.UploadDirItem;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.ServiceId;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static nu.marginalia.functions.execution.api.ExecutorApiGrpc.*;

@Singleton
public class ExecutorClient {
    private final GrpcMultiNodeChannelPool<ExecutorApiBlockingStub> channelPool;
    private static final Logger logger = LoggerFactory.getLogger(ExecutorClient.class);
    private final ServiceRegistryIf registry;

    @Inject
    public ExecutorClient(ServiceRegistryIf registry,
                          GrpcChannelPoolFactory grpcChannelPoolFactory)
    {
        this.registry = registry;
        this.channelPool = grpcChannelPoolFactory
                .createMulti(
                        ServiceKey.forGrpcApi(ExecutorApiGrpc.class, ServicePartition.multi()),
                        ExecutorApiGrpc::newBlockingStub);
    }

    public void startFsm(int node, String actorName) {
        channelPool.call(ExecutorApiBlockingStub::startFsm)
                .forNode(node)
                .run(RpcFsmName.newBuilder()
                        .setActorName(actorName)
                        .build());

    }

    public void stopFsm(int node, String actorName) {
        channelPool.call(ExecutorApiBlockingStub::stopFsm)
                .forNode(node)
                .run(RpcFsmName.newBuilder()
                        .setActorName(actorName)
                        .build());
    }

    public void stopProcess(int node, String id) {
        channelPool.call(ExecutorApiBlockingStub::stopProcess)
                .forNode(node)
                .run(RpcProcessId.newBuilder()
                        .setProcessId(id)
                        .build());

    }

    public void calculateAdjacencies(int node) {
        channelPool.call(ExecutorApiBlockingStub::calculateAdjacencies)
                .forNode(node)
                .run(Empty.getDefaultInstance());
    }



    public void downloadSampleData(int node, String sampleSet) {
        channelPool.call(ExecutorApiBlockingStub::downloadSampleData)
                .forNode(node)
                .run(RpcDownloadSampleData.newBuilder()
                        .setSampleSet(sampleSet)
                        .build());
    }

    public void restoreBackup(int nodeId, FileStorageId toLoad) {
        channelPool.call(ExecutorApiBlockingStub::restoreBackup)
                .forNode(nodeId)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(toLoad.id())
                        .build());
    }

    public ActorRunStates getActorStates(int node) {
        try {
            var rs = channelPool.call(ExecutorApiBlockingStub::getActorStates)
                    .forNode(node)
                    .run(Empty.getDefaultInstance());
            var states = rs.getActorRunStatesList().stream()
                    .map(r -> new ActorRunState(
                            r.getActorName(),
                            r.getState(),
                            r.getActorDescription(),
                            r.getStateDescription(),
                            r.getTerminal(),
                            r.getCanStart())
                    )
                    .toList();

            return new ActorRunStates(node, states);
        }
        catch (Exception ex) {
            logger.warn("Failed to get actor states", ex);

            // Return an empty list of states to avoid breaking the UI when a node is down
            return new ActorRunStates(node, List.of());
        }
    }

    public UploadDirContents listSideloadDir(int node) {
        try {
            var rs = channelPool.call(ExecutorApiBlockingStub::listSideloadDir)
                    .forNode(node)
                    .run(Empty.getDefaultInstance());
            var items = rs.getEntriesList().stream()
                    .map(i -> new UploadDirItem(i.getName(), i.getLastModifiedTime(), i.getIsDirectory(), i.getSize()))
                    .toList();
            return new UploadDirContents(rs.getPath(), items);
        }
        catch (Exception ex) {
            logger.warn("Failed to list sideload dir", ex);

            // Return an empty list of items to avoid breaking the UI when a node is down
            return new UploadDirContents("", List.of());
        }
    }

    public FileStorageContent listFileStorage(int node, FileStorageId fileId) {
        try {
            var rs = channelPool.call(ExecutorApiBlockingStub::listFileStorage)
                    .forNode(node)
                    .run(RpcFileStorageId.newBuilder()
                            .setFileStorageId(fileId.id())
                            .build()
                    );

            return new FileStorageContent(rs.getEntriesList().stream()
                    .map(e -> new FileStorageFile(e.getName(), e.getSize(), e.getLastModifiedTime()))
                    .toList());
        }
        catch (Exception ex) {
            logger.warn("Failed to list file storage", ex);

            // Return an empty list of items to avoid breaking the UI when a node is down
            return new FileStorageContent(List.of());
        }
    }

    public URL remoteFileURL(FileStorage fileStorage, String path) {
        String uriPath = STR."/transfer/file/\{fileStorage.id()}";
        String uriQuery = STR."path=\{URLEncoder.encode(path, StandardCharsets.UTF_8)}";

        var service = registry.getEndpoints(ServiceKey.forRest(ServiceId.Executor, fileStorage.node()))
                .stream().findFirst().orElseThrow();

        try {
            return service.endpoint().toURL(uriPath, uriQuery);
        }
        catch (URISyntaxException|MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

}
