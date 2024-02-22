package nu.marginalia.executor.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.client.GrpcMultiNodeChannelPool;
import nu.marginalia.executor.api.*;
import nu.marginalia.executor.api.ExecutorApiGrpc.ExecutorApiBlockingStub;
import nu.marginalia.executor.model.ActorRunState;
import nu.marginalia.executor.model.ActorRunStates;
import nu.marginalia.executor.storage.FileStorageContent;
import nu.marginalia.executor.storage.FileStorageFile;
import nu.marginalia.executor.upload.UploadDirContents;
import nu.marginalia.executor.upload.UploadDirItem;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.storage.model.FileStorageId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

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

    public void triggerCrawl(int node, FileStorageId fid) {
        channelPool.call(ExecutorApiBlockingStub::triggerCrawl)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }

    public void triggerRecrawl(int node, FileStorageId fid) {
        channelPool.call(ExecutorApiBlockingStub::triggerRecrawl)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }

    public void triggerConvert(int node, FileStorageId fid) {
        channelPool.call(ExecutorApiBlockingStub::triggerConvert)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }

    public void triggerConvertAndLoad(int node, FileStorageId fid) {
        channelPool.call(ExecutorApiBlockingStub::triggerConvertAndLoad)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }

    public void loadProcessedData(int node, List<FileStorageId> ids) {
        channelPool.call(ExecutorApiBlockingStub::loadProcessedData)
                .forNode(node)
                .run(RpcFileStorageIds.newBuilder()
                        .addAllFileStorageIds(ids.stream().map(FileStorageId::id).toList())
                        .build());
    }

    public void calculateAdjacencies(int node) {
        channelPool.call(ExecutorApiBlockingStub::calculateAdjacencies)
                .forNode(node)
                .run(Empty.getDefaultInstance());
    }

    public void sideloadEncyclopedia(int node, Path sourcePath, String baseUrl) {
        channelPool.call(ExecutorApiBlockingStub::sideloadEncyclopedia)
                .forNode(node)
                .run(RpcSideloadEncyclopedia.newBuilder()
                        .setBaseUrl(baseUrl)
                        .setSourcePath(sourcePath.toString())
                        .build());
    }

    public void sideloadDirtree(int node, Path sourcePath) {
        channelPool.call(ExecutorApiBlockingStub::sideloadDirtree)
                .forNode(node)
                .run(RpcSideloadDirtree.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build());
    }
    public void sideloadReddit(int node, Path sourcePath) {
        channelPool.call(ExecutorApiBlockingStub::sideloadReddit)
                .forNode(node)
                .run(RpcSideloadReddit.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build());
    }
    public void sideloadWarc(int node, Path sourcePath) {
        channelPool.call(ExecutorApiBlockingStub::sideloadWarc)
                .forNode(node)
                .run(RpcSideloadWarc.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build());
    }

    public void sideloadStackexchange(int node, Path sourcePath) {
        channelPool.call(ExecutorApiBlockingStub::sideloadStackexchange)
                .forNode(node)
                .run(RpcSideloadStackexchange.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build());
    }

    public void createCrawlSpecFromDownload(int node, String description, String url) {
        channelPool.call(ExecutorApiBlockingStub::createCrawlSpecFromDownload)
                .forNode(node)
                .run(RpcCrawlSpecFromDownload.newBuilder()
                        .setDescription(description)
                        .setUrl(url)
                        .build());
    }

    public void exportAtags(int node, FileStorageId fid) {
        channelPool.call(ExecutorApiBlockingStub::exportAtags)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }
    public void exportSampleData(int node, FileStorageId fid, int size, String name) {
        channelPool.call(ExecutorApiBlockingStub::exportSampleData)
                .forNode(node)
                .run(RpcExportSampleData.newBuilder()
                        .setFileStorageId(fid.id())
                        .setSize(size)
                        .setName(name)
                        .build());
    }

    public void exportRssFeeds(int node, FileStorageId fid) {
        channelPool.call(ExecutorApiBlockingStub::exportRssFeeds)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }
    public void exportTermFrequencies(int node, FileStorageId fid) {
        channelPool.call(ExecutorApiBlockingStub::exportTermFrequencies)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build());
    }

    public void downloadSampleData(int node, String sampleSet) {
        channelPool.call(ExecutorApiBlockingStub::downloadSampleData)
                .forNode(node)
                .run(RpcDownloadSampleData.newBuilder()
                        .setSampleSet(sampleSet)
                        .build());
    }

    public void exportData(int node) {
        channelPool.call(ExecutorApiBlockingStub::exportData)
                .forNode(node)
                .run(Empty.getDefaultInstance());
    }

    public void restoreBackup(int node, FileStorageId fid) {
        channelPool.call(ExecutorApiBlockingStub::restoreBackup)
                .forNode(node)
                .run(RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
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

    public void transferFile(int node, FileStorageId fileId, String path, OutputStream destOutputStream) {
        String uriPath = STR."/transfer/file/\{fileId.id()}";
        String uriQuery = STR."path=\{URLEncoder.encode(path, StandardCharsets.UTF_8)}";

        var service = registry.getEndpoints(ServiceKey.forRest(ServiceId.Executor, node))
                .stream().findFirst().orElseThrow();

        try (var urlStream = service.endpoint().toURL(uriPath, uriQuery).openStream()) {
            urlStream.transferTo(destOutputStream);
        }
        catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }


}
