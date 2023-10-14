package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.client.Context;
import nu.marginalia.control.Redirects;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.executor.model.ActorRunState;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.List;

@Singleton
public class ControlActorService {

    private final ExecutorClient executorClient;

    @Inject
    public ControlActorService(ExecutorClient executorClient) {
        this.executorClient = executorClient;
    }

    public void register() {
        Spark.post("/public/nodes/:node/storage/:fid/crawl", this::triggerCrawling, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/storage/:fid/process", this::triggerProcessing, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/storage/:fid/process-and-load", this::triggerProcessingWithLoad, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/storage/:fid/load", this::loadProcessedData, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/storage/:fid/restore-backup", this::restoreBackup, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/storage/specs", this::createCrawlSpecification, Redirects.redirectToStorage);

        Spark.post("/public/nodes/:node/fsms/:fsm/start", this::startFsm, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/fsms/:fsm/stop", this::stopFsm, Redirects.redirectToActors);

    }

    public Object startFsm(Request req, Response rsp) throws Exception {
        executorClient.startFsm(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fsm").toUpperCase());

        return "";
    }

    public Object stopFsm(Request req, Response rsp) throws Exception {
        executorClient.stopFsm(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fsm").toUpperCase());

        return "";
    }

    public Object triggerCrawling(Request req, Response response) throws Exception {
        executorClient.triggerCrawl(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fid"));

        return "";
    }

    public Object triggerProcessing(Request req, Response response) throws Exception {
        executorClient.triggerConvert(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fid"));

        return "";
    }

    public Object triggerProcessingWithLoad(Request req, Response response) throws Exception {
        executorClient.triggerProcessAndLoad(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fid"));

        return "";
    }

    public Object loadProcessedData(Request req, Response response) throws Exception {
        executorClient.loadProcessedData(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fid"));

        return "";
    }

    public List<ActorRunState> getActorStates(Request req) {
        return executorClient.getActorStates(Context.fromRequest(req), Integer.parseInt(req.params("node"))).states();
    }

    public Object createCrawlSpecification(Request request, Response response) throws Exception {
        final String description = request.queryParams("description");
        final String url = request.queryParams("url");
        final String source = request.queryParams("source");

        if ("db".equals(source)) {
            executorClient.createCrawlSpecFromDb(Context.fromRequest(request), 0, description);
        }
        else if ("download".equals(source)) {
            executorClient.createCrawlSpecFromDownload(Context.fromRequest(request), 0, description, url);
        }
        else {
            throw new IllegalArgumentException("Unknown source: " + source);
        }

        return "";
    }

    public Object restoreBackup(Request req, Response response) throws Exception {
        executorClient.restoreBackup(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fid"));

        return "";
    }


}