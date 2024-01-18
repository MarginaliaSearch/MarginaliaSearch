package nu.marginalia.executor.client;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.client.exception.RouteNotConfiguredException;
import nu.marginalia.executor.model.ActorRunStates;
import nu.marginalia.executor.model.load.LoadParameters;
import nu.marginalia.executor.model.transfer.TransferItem;
import nu.marginalia.executor.model.transfer.TransferSpec;
import nu.marginalia.executor.storage.FileStorageContent;
import nu.marginalia.executor.upload.UploadDirContents;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ExecutorClient extends AbstractDynamicClient {
    @Inject
    public ExecutorClient(ServiceDescriptors descriptors) {
        super(descriptors.forId(ServiceId.Executor), GsonFactory::get);
    }

    public void startFsm(Context ctx, int node, String actorName) {
        post(ctx, node, "/actor/"+actorName+"/start", "").blockingSubscribe();
    }

    public void stopFsm(Context ctx, int node, String actorName) {
        post(ctx, node, "/actor/"+actorName+"/stop", "").blockingSubscribe();
    }

    public void stopProcess(Context ctx, int node, String id) {
        post(ctx, node, "/process/" + id + "/stop", "").blockingSubscribe();
    }


    public void triggerCrawl(Context ctx, int node, FileStorageId fid) {
        post(ctx, node, "/process/crawl/" + fid, "").blockingSubscribe();
    }

    public void triggerRecrawl(Context ctx, int node, FileStorageId fid) {
        post(ctx, node, "/process/recrawl", fid).blockingSubscribe();
    }

    public void triggerConvert(Context ctx, int node, FileStorageId fid) {
        post(ctx, node, "/process/convert/" + fid.id(), "").blockingSubscribe();
    }

    public void triggerConvertAndLoad(Context ctx, int node, FileStorageId fid) {
        post(ctx, node, "/process/convert-load/" + fid.id(), "").blockingSubscribe();
    }

    public void loadProcessedData(Context ctx, int node, LoadParameters ids) {
        post(ctx, node, "/process/load", ids).blockingSubscribe();
    }

    public void calculateAdjacencies(Context ctx, int node) {
        post(ctx, node, "/process/adjacency-calculation", "").blockingSubscribe();
    }

    public void sideloadEncyclopedia(Context ctx, int node, Path sourcePath, String baseUrl) {
        post(ctx, node,
                "/sideload/encyclopedia?path="+ URLEncoder.encode(sourcePath.toString(), StandardCharsets.UTF_8) + "&baseUrl=" + URLEncoder.encode(baseUrl, StandardCharsets.UTF_8),
                "").blockingSubscribe();

    }

    public void sideloadDirtree(Context ctx, int node, Path sourcePath) {
        post(ctx, node,
                "/sideload/dirtree?path="+ URLEncoder.encode(sourcePath.toString(), StandardCharsets.UTF_8),
                "").blockingSubscribe();
    }

    public void sideloadWarc(Context ctx, int node, Path sourcePath) {
        post(ctx, node,
                "/sideload/warc?path="+ URLEncoder.encode(sourcePath.toString(), StandardCharsets.UTF_8),
                "").blockingSubscribe();
    }

    public void sideloadStackexchange(Context ctx, int node, Path sourcePath) {
        post(ctx, node,
                "/sideload/stackexchange?path="+URLEncoder.encode(sourcePath.toString(), StandardCharsets.UTF_8),
                "").blockingSubscribe();
    }

    public void createCrawlSpecFromDownload(Context context, int node, String description, String url) {
        post(context, node, "/process/crawl-spec/from-download?description="+URLEncoder.encode(description, StandardCharsets.UTF_8)+"&url="+URLEncoder.encode(url, StandardCharsets.UTF_8), "")
                .blockingSubscribe();
    }

    public void exportAtags(Context ctx, int node, FileStorageId fid) {
        post(ctx, node, "/export/atags?fid="+fid, "").blockingSubscribe();
    }
    public void exportRssFeeds(Context ctx, int node, FileStorageId fid) {
        post(ctx, node, "/export/feeds?fid="+fid, "").blockingSubscribe();
    }
    public void exportTermFrequencies(Context ctx, int node, FileStorageId fid) {
        post(ctx, node, "/export/termfreq?fid="+fid, "").blockingSubscribe();
    }

    public void exportData(Context ctx, int node) {
        post(ctx, node, "/export/data", "").blockingSubscribe();
    }

    public void restoreBackup(Context context, int node, FileStorageId fid) {
        post(context, node, "/backup/" + fid + "/restore", "").blockingSubscribe();
    }

    public ActorRunStates getActorStates(Context context, int node) {
        try {
            return get(context, node, "/actor", ActorRunStates.class).blockingFirst();
        }
        catch (RouteNotConfiguredException ex) {
            // node is down, return dummy data
            return new ActorRunStates(node, new ArrayList<>());
        }
    }

    public UploadDirContents listSideloadDir(Context context, int node) {
        try {
            return get(context, node, "/sideload/", UploadDirContents.class).blockingFirst();
        }
        catch (RouteNotConfiguredException ex) {
            // node is down, return dummy data
            return new UploadDirContents("/", new ArrayList<>());
        }
    }

    public FileStorageContent listFileStorage(Context context, int node, FileStorageId fileId) {
        try {
            return get(context, node, "/storage/"+fileId.id(), FileStorageContent.class).blockingFirst();
        }
        catch (RouteNotConfiguredException ex) {
            // node is down, return dummy data
            return new FileStorageContent(new ArrayList<>());
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
