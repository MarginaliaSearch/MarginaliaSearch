package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.client.Context;
import nu.marginalia.control.Redirects;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.IndexMqEndpoints;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ServiceConfiguration;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Singleton
public class ControlNodeActionsService {

    private final IndexClient indexClient;
    private final ServiceEventLog eventLog;
    private final ExecutorClient executorClient;

    @Inject
    public ControlNodeActionsService(ExecutorClient executorClient,
                                     IndexClient indexClient,
                                     ServiceEventLog eventLog)
    {
        this.executorClient = executorClient;

        this.indexClient = indexClient;
        this.eventLog = eventLog;

    }

    public void register() {
        Spark.post("/public/nodes/:node/actions/repartition-index", this::triggerRepartition, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/actions/sideload-encyclopedia", this::sideloadEncyclopedia, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/actions/sideload-dirtree", this::sideloadDirtree, Redirects.redirectToActors);
        Spark.post("/public/nodes/:node/actions/sideload-stackexchange", this::sideloadStackexchange, Redirects.redirectToActors);
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
        indexClient.outbox().sendAsync(IndexMqEndpoints.INDEX_REPARTITION, "");
        return "";
    }


}
