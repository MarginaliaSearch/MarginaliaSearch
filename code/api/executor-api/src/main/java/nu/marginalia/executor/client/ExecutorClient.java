package nu.marginalia.executor.client;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.client.route.RouteProvider;
import nu.marginalia.client.route.ServiceRoute;
import nu.marginalia.executor.storage.FileStorageContent;
import nu.marginalia.service.descriptor.ServiceDescriptor;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.executor.model.ActorRunStates;
import nu.marginalia.executor.model.crawl.RecrawlParameters;
import nu.marginalia.executor.model.load.LoadParameters;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

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

    public void triggerCrawl(Context ctx, int node, String fid) {
        post(ctx, node, "/process/crawl/" + fid, "").blockingSubscribe();
    }

    public void triggerRecrawl(Context ctx, int node, RecrawlParameters parameters) {
        post(ctx, node, "/process/recrawl", parameters).blockingSubscribe();
    }

    public void triggerConvert(Context ctx, int node, FileStorageId fid) {
        post(ctx, node, "/process/convert/" + fid.id(), "").blockingSubscribe();
    }
    @Deprecated
    public void triggerConvert(Context ctx, int node, String fid) {
        post(ctx, node, "/process/convert/" + fid, "").blockingSubscribe();
    }

    public void triggerProcessAndLoad(Context ctx, int node, String fid) {
        post(ctx, node, "/process/convert-load/" + fid, "").blockingSubscribe();
    }

    @Deprecated
    public void loadProcessedData(Context ctx, int node, String fid) {
        loadProcessedData(ctx, node, new LoadParameters(List.of(new FileStorageId(Long.parseLong(fid)))));
    }

    public void loadProcessedData(Context ctx, int node, LoadParameters ids) {
        post(ctx, node, "/process/load", ids).blockingSubscribe();
    }

    public void calculateAdjacencies(Context ctx, int node) {
        post(ctx, node, "/process/adjacency-calculation", "").blockingSubscribe();
    }

    public void exportData(Context ctx) {
//        post(ctx, node, "/process/adjacency-calculation/", "").blockingSubscribe();
        // FIXME this shouldn't be done in the executor
    }

    public void sideloadEncyclopedia(Context ctx, int node, Path sourcePath) {
        post(ctx, node,
                "/sideload/encyclopedia?path="+ URLEncoder.encode(sourcePath.toString(), StandardCharsets.UTF_8),
                "").blockingSubscribe();

    }

    public void sideloadDirtree(Context ctx, int node, Path sourcePath) {
        post(ctx, node,
                "/sideload/dirtree?path="+ URLEncoder.encode(sourcePath.toString(), StandardCharsets.UTF_8),
                "").blockingSubscribe();
    }

    public void sideloadStackexchange(Context ctx, int node, Path sourcePath) {
        post(ctx, node,
                "/sideload/stackexchange?path="+URLEncoder.encode(sourcePath.toString(), StandardCharsets.UTF_8),
                "").blockingSubscribe();
    }

    public void createCrawlSpecFromDb(Context context, int node, String description) {
        post(context, node, "/process/crawl-spec/from-db?description="+URLEncoder.encode(description, StandardCharsets.UTF_8), "")
            .blockingSubscribe();
    }

    public void createCrawlSpecFromDownload(Context context, int node, String description, String url) {
        post(context, node, "/process/crawl-spec/from-download?description="+URLEncoder.encode(description, StandardCharsets.UTF_8)+"&url="+URLEncoder.encode(url, StandardCharsets.UTF_8), "")
                .blockingSubscribe();
    }

    public void restoreBackup(Context context, int node, String fid) {
        post(context, node, "/backup/" + fid + "/restore", "").blockingSubscribe();
    }

    public ActorRunStates getActorStates(Context context, int node) {
        return get(context, node, "/actor", ActorRunStates.class).blockingFirst();
    }

    public FileStorageContent listFileStorage(Context context, int node, FileStorageId fileId) {
        return get(context, node, "/storage/"+fileId.id(), FileStorageContent.class).blockingFirst();
    }

}
