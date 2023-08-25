package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.actor.ControlActors;
import nu.marginalia.control.actor.task.*;
import nu.marginalia.control.actor.Actor;
import nu.marginalia.control.model.ActorRunState;
import nu.marginalia.control.model.ActorStateGraph;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorStateInstance;
import spark.Request;
import spark.Response;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ControlActorService {
    private final ControlActors controlActors;

    @Inject
    public ControlActorService(ControlActors controlActors) {
        this.controlActors = controlActors;
    }

    public Object getActorStateGraph(Actor actor) {
        var currentState = controlActors.getActorStates().get(actor);

        return new ActorStateGraph(controlActors.getActorDefinition(actor), currentState);
    }

    public Object startFsm(Request req, Response rsp) throws Exception {
        controlActors.start(
                Actor.valueOf(req.params("fsm").toUpperCase())
        );
        return "";
    }

    public Object stopFsm(Request req, Response rsp) throws Exception {
        controlActors.stop(
                Actor.valueOf(req.params("fsm").toUpperCase())
        );
        return "";
    }

    public Object triggerCrawling(Request request, Response response) throws Exception {
        controlActors.start(
                Actor.CRAWL,
                FileStorageId.parse(request.params("fid"))
        );
        return "";
    }

    public Object triggerRecrawling(Request request, Response response) throws Exception {
        controlActors.start(
                Actor.RECRAWL,
                RecrawlActor.recrawlFromCrawlData(
                        FileStorageId.parse(request.params("fid"))
                )
        );
        return "";
    }

    public Object triggerProcessing(Request request, Response response) throws Exception {
        controlActors.startFrom(
                Actor.CONVERT,
                ConvertActor.CONVERT,
                FileStorageId.parse(request.params("fid"))
        );

        return "";
    }

    public Object triggerProcessingWithLoad(Request request, Response response) throws Exception {
        controlActors.start(
                Actor.CONVERT_AND_LOAD,
                FileStorageId.parse(request.params("fid"))
        );
        return "";
    }

    public Object loadProcessedData(Request request, Response response) throws Exception {
        var fid = FileStorageId.parse(request.params("fid"));

        // Start the FSM from the intermediate state that triggers the load
        controlActors.startFrom(
                Actor.CONVERT_AND_LOAD,
                ConvertAndLoadActor.LOAD,
                new ConvertAndLoadActor.Message(null, fid, 0L, 0L)
        );

        return "";
    }

    private final ConcurrentHashMap<String, String> actorStateDescriptions = new ConcurrentHashMap<>();

    public Object getActorStates() {
        return controlActors.getActorStates().entrySet().stream().map(e -> {

            final var stateGraph = controlActors.getActorDefinition(e.getKey());

            final ActorStateInstance state = e.getValue();
            final String actorDescription = stateGraph.describe();

            final String machineName = e.getKey().name();
            final String stateName = state.name();

            final String stateDescription = actorStateDescriptions.computeIfAbsent(
                    (machineName + "." + stateName),
                    k -> Optional.ofNullable(stateGraph.declaredStates().get(stateName))
                            .map(ActorState::description)
                            .orElse("Description missing for " + stateName)
            );



            final boolean terminal = state.isFinal();
            final boolean canStart = controlActors.isDirectlyInitializable(e.getKey()) && terminal;

            return new ActorRunState(machineName,
                    stateName,
                    actorDescription,
                    stateDescription,
                    terminal,
                    canStart);
        })
                .filter(s -> !s.terminal() || s.canStart())
                .sorted(Comparator.comparing(ActorRunState::name))
                .toList();
    }

    public Object createCrawlSpecification(Request request, Response response) throws Exception {
        final String description = request.queryParams("description");
        final String url = request.queryParams("url");
        final String source = request.queryParams("source");

        if ("db".equals(source)) {
            controlActors.startFrom(Actor.CRAWL_JOB_EXTRACTOR,
                    CrawlJobExtractorActor.CREATE_FROM_DB,
                    new CrawlJobExtractorActor.CrawlJobExtractorArguments(description)
            );
        }
        else if ("download".equals(source)) {
            controlActors.startFrom(Actor.CRAWL_JOB_EXTRACTOR,
                    CrawlJobExtractorActor.CREATE_FROM_LINK,
                    new CrawlJobExtractorActor.CrawlJobExtractorArgumentsWithURL(description, url)
            );
        }
        else {
            throw new IllegalArgumentException("Unknown source: " + source);
        }

        return "";
    }

    public Object restoreBackup(Request request, Response response) throws Exception {
        var fid = FileStorageId.parse(request.params("fid"));
        controlActors.startFrom(Actor.RESTORE_BACKUP, RestoreBackupActor.RESTORE, fid);
        return "";
    }


}