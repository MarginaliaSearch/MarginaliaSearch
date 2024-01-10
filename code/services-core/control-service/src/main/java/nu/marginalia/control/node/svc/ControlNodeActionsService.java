package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.client.Context;
import nu.marginalia.control.RedirectControl;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.service.control.ServiceEventLog;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class ControlNodeActionsService {

    private final IndexClient indexClient;
    private final RedirectControl redirectControl;
    private final ServiceEventLog eventLog;
    private final ExecutorClient executorClient;

    @Inject
    public ControlNodeActionsService(ExecutorClient executorClient,
                                     IndexClient indexClient,
                                     RedirectControl redirectControl,
                                     ServiceEventLog eventLog)
    {
        this.executorClient = executorClient;

        this.indexClient = indexClient;
        this.redirectControl = redirectControl;
        this.eventLog = eventLog;

    }

    public void register() {
        Spark.post("/public/nodes/:node/actions/repartition-index", this::triggerRepartition,
                redirectControl.renderRedirectAcknowledgement("Repartitioning", "..")
        );
        Spark.post("/public/nodes/:node/actions/sideload-encyclopedia", this::sideloadEncyclopedia,
                redirectControl.renderRedirectAcknowledgement("Sideloading", "..")
        );
        Spark.post("/public/nodes/:node/actions/sideload-dirtree", this::sideloadDirtree,
                redirectControl.renderRedirectAcknowledgement("Sideloading", "..")
        );
        Spark.post("/public/nodes/:node/actions/sideload-warc", this::sideloadWarc,
                redirectControl.renderRedirectAcknowledgement("Sideloading", "..")
        );
        Spark.post("/public/nodes/:node/actions/sideload-stackexchange", this::sideloadStackexchange,
                redirectControl.renderRedirectAcknowledgement("Sideloading", "..")
        );
    }

    public Object sideloadEncyclopedia(Request request, Response response) throws Exception {

        Path sourcePath = Path.of(request.queryParams("source"));
        if (!Files.exists(sourcePath)) {
            Spark.halt(404);
            return "No such file " + sourcePath;
        }
        String baseUrl = request.queryParams("baseUrl");

        final int nodeId = Integer.parseInt(request.params("node"));

        eventLog.logEvent("USER-ACTION", "SIDELOAD ENCYCLOPEDIA " + nodeId);

        executorClient.sideloadEncyclopedia(Context.fromRequest(request), nodeId, sourcePath, baseUrl);

        return "";
    }

    public Object sideloadDirtree(Request request, Response response) throws Exception {

        Path sourcePath = Path.of(request.queryParams("source"));
        if (!Files.exists(sourcePath)) {
            Spark.halt(404);
            return "No such file " + sourcePath;
        }

        final int nodeId = Integer.parseInt(request.params("node"));

        eventLog.logEvent("USER-ACTION", "SIDELOAD DIRTREE " + nodeId);

        executorClient.sideloadDirtree(Context.fromRequest(request), nodeId, sourcePath);

        return "";
    }

    public Object sideloadWarc(Request request, Response response) throws Exception {

        Path sourcePath = Path.of(request.queryParams("source"));
        if (!Files.exists(sourcePath)) {
            Spark.halt(404);
            return "No such file " + sourcePath;
        }

        final int nodeId = Integer.parseInt(request.params("node"));

        eventLog.logEvent("USER-ACTION", "SIDELOAD WARC " + nodeId);

        executorClient.sideloadWarc(Context.fromRequest(request), nodeId, sourcePath);

        return "";
    }
    public Object sideloadStackexchange(Request request, Response response) throws Exception {

        Path sourcePath = Path.of(request.queryParams("source"));
        if (!Files.exists(sourcePath)) {
            Spark.halt(404);
            return "No such file " + sourcePath;
        }

        final int nodeId = Integer.parseInt(request.params("node"));

        eventLog.logEvent("USER-ACTION", "SIDELOAD STACKEXCHANGE " + nodeId);

        executorClient.sideloadStackexchange(Context.fromRequest(request), nodeId, sourcePath);
        return "";
    }

    public Object triggerRepartition(Request request, Response response) throws Exception {
        indexClient.triggerRepartition(Integer.parseInt(request.params("node")));

        return "";
    }


}
