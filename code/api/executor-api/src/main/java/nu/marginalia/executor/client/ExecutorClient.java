package nu.marginalia.executor.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.client.grpc.GrpcStubPool;
import nu.marginalia.executor.api.*;
import nu.marginalia.executor.api.ExecutorApiGrpc.ExecutorApiBlockingStub;
import nu.marginalia.executor.model.ActorRunState;
import nu.marginalia.executor.model.ActorRunStates;
import nu.marginalia.executor.model.transfer.TransferItem;
import nu.marginalia.executor.model.transfer.TransferSpec;
import nu.marginalia.executor.storage.FileStorageContent;
import nu.marginalia.executor.storage.FileStorageFile;
import nu.marginalia.executor.upload.UploadDirContents;
import nu.marginalia.executor.upload.UploadDirItem;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.storage.model.FileStorageId;

import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class ExecutorClient extends AbstractDynamicClient {
    private final GrpcStubPool<ExecutorApiBlockingStub> stubPool;
    private static final Logger logger = LoggerFactory.getLogger(ExecutorClient.class);

    @Inject
    public ExecutorClient(ServiceDescriptors descriptors, NodeConfigurationService nodeConfigurationService) {
        super(descriptors.forId(ServiceId.Executor), GsonFactory::get);

        stubPool = new GrpcStubPool<>(ServiceId.Executor) {
            @Override
            public ExecutorApiBlockingStub createStub(ManagedChannel channel) {
                return ExecutorApiGrpc.newBlockingStub(channel);
            }

            @Override
            public List<Integer> getEligibleNodes() {
                return nodeConfigurationService.getAll()
                        .stream()
                        .map(NodeConfiguration::node)
                        .toList();
            }
        };
    }

    public void startFsm(int node, String actorName) {
        stubPool.apiForNode(node).startFsm(
                RpcFsmName.newBuilder()
                        .setActorName(actorName)
                        .build()
        );
    }

    public void stopFsm(int node, String actorName) {
        stubPool.apiForNode(node).stopFsm(
                RpcFsmName.newBuilder()
                        .setActorName(actorName)
                        .build()
        );
    }

    public void stopProcess(int node, String id) {
        stubPool.apiForNode(node).stopProcess(
                RpcProcessId.newBuilder()
                        .setProcessId(id)
                        .build()
        );
    }

    public void triggerCrawl(int node, FileStorageId fid) {
        stubPool.apiForNode(node).triggerCrawl(
                RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build()
        );
    }

    public void triggerRecrawl(int node, FileStorageId fid) {
        stubPool.apiForNode(node).triggerRecrawl(
                RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build()
        );
    }

    public void triggerConvert(int node, FileStorageId fid) {
        stubPool.apiForNode(node).triggerConvert(
                RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build()
        );
    }

    public void triggerConvertAndLoad(int node, FileStorageId fid) {
        stubPool.apiForNode(node).triggerConvertAndLoad(
                RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build()
        );
    }

    public void loadProcessedData(int node, List<FileStorageId> ids) {
        stubPool.apiForNode(node).loadProcessedData(
                RpcFileStorageIds.newBuilder()
                        .addAllFileStorageIds(ids.stream().map(FileStorageId::id).toList())
                        .build()
        );
    }

    public void calculateAdjacencies(int node) {
        stubPool.apiForNode(node).calculateAdjacencies(Empty.getDefaultInstance());
    }

    public void sideloadEncyclopedia(int node, Path sourcePath, String baseUrl) {
        stubPool.apiForNode(node).sideloadEncyclopedia(
                RpcSideloadEncyclopedia.newBuilder()
                        .setBaseUrl(baseUrl)
                        .setSourcePath(sourcePath.toString())
                        .build()
        );
    }

    public void sideloadDirtree(int node, Path sourcePath) {
        stubPool.apiForNode(node).sideloadDirtree(
                RpcSideloadDirtree.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build()
        );
    }
    public void sideloadReddit(int node, Path sourcePath) {
        stubPool.apiForNode(node).sideloadReddit(
                RpcSideloadReddit.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build()
        );
    }
    public void sideloadWarc(int node, Path sourcePath) {
        stubPool.apiForNode(node).sideloadWarc(
                RpcSideloadWarc.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build()
        );
    }

    public void sideloadStackexchange(int node, Path sourcePath) {
        stubPool.apiForNode(node).sideloadStackexchange(
                RpcSideloadStackexchange.newBuilder()
                        .setSourcePath(sourcePath.toString())
                        .build()
        );
    }

    public void createCrawlSpecFromDownload(int node, String description, String url) {
        stubPool.apiForNode(node).createCrawlSpecFromDownload(
                RpcCrawlSpecFromDownload.newBuilder()
                        .setDescription(description)
                        .setUrl(url)
                        .build()
        );
    }

    public void exportAtags(int node, FileStorageId fid) {
        stubPool.apiForNode(node).exportAtags(
                RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build()
        );
    }
    public void exportSampleData(int node, FileStorageId fid, int size, String name) {
        stubPool.apiForNode(node).exportSampleData(
                RpcExportSampleData.newBuilder()
                        .setFileStorageId(fid.id())
                        .setSize(size)
                        .setName(name)
                        .build()
        );
    }

    public void exportRssFeeds(int node, FileStorageId fid) {
        stubPool.apiForNode(node).exportRssFeeds(
                RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build()
        );
    }
    public void exportTermFrequencies(int node, FileStorageId fid) {
        stubPool.apiForNode(node).exportTermFrequencies(
                RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build()
        );
    }

    public void downloadSampleData(int node, String sampleSet) {
        stubPool.apiForNode(node).downloadSampleData(
                RpcDownloadSampleData.newBuilder()
                        .setSampleSet(sampleSet)
                        .build()
        );
    }

    public void exportData(int node) {
        stubPool.apiForNode(node).exportData(Empty.getDefaultInstance());
    }

    public void restoreBackup(int node, FileStorageId fid) {
        stubPool.apiForNode(node).restoreBackup(
                RpcFileStorageId.newBuilder()
                        .setFileStorageId(fid.id())
                        .build()
        );
    }

    public ActorRunStates getActorStates(int node) {
        try {
            var rs = stubPool.apiForNode(node).getActorStates(Empty.getDefaultInstance());
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
            var rs = stubPool.apiForNode(node).listSideloadDir(Empty.getDefaultInstance());
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
            var rs = stubPool.apiForNode(node).listFileStorage(
                    RpcFileStorageId.newBuilder()
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

    public void transferFile(Context context, int node, FileStorageId fileId, String path, OutputStream destOutputStream) {
        String endpoint = "/transfer/file/%d?path=%s".formatted(fileId.id(), URLEncoder.encode(path, StandardCharsets.UTF_8));

        get(context, node, endpoint,
                destOutputStream)
                .blockingSubscribe();
    }

    public TransferSpec getTransferSpec(Context context, int node, int count) {
        return get(context, node, "/transfer/spec?count="+count, TransferSpec.class)
                .timeout(30, TimeUnit.MINUTES)
                .blockingFirst();
    }

    public void yieldDomain(Context context, int node, TransferItem item) {
        post(context, node, "/transfer/yield", item).blockingSubscribe();
    }

}
