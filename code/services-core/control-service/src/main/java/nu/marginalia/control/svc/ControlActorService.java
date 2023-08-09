package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.actor.ControlActors;
import nu.marginalia.control.actor.task.CrawlJobExtractorActor;
import nu.marginalia.control.actor.task.ReconvertAndLoadActor;
import nu.marginalia.control.actor.task.RecrawlActor;
import nu.marginalia.control.actor.Actor;
import nu.marginalia.control.model.ActorRunState;
import nu.marginalia.control.model.ActorStateGraph;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.mqsm.state.MachineState;
import spark.Request;
import spark.Response;

import java.util.Comparator;

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
        controlActors.start(
                Actor.RECONVERT_LOAD,
                FileStorageId.parse(request.params("fid"))
        );
        return "";
    }

    public Object loadProcessedData(Request request, Response response) throws Exception {
        var fid = FileStorageId.parse(request.params("fid"));

        // Start the FSM from the intermediate state that triggers the load
        controlActors.startFrom(
                Actor.RECONVERT_LOAD,
                ReconvertAndLoadActor.LOAD,
                new ReconvertAndLoadActor.Message(null, fid, 0L, 0L)
        );

        return "";
    }

    public Object getActorStates() {
        return controlActors.getActorStates().entrySet().stream().map(e -> {

            final MachineState state = e.getValue();
            final String machineName = e.getKey().name();
            final String stateName = state.name();
            final boolean terminal = state.isFinal();
            final boolean canStart = controlActors.isDirectlyInitializable(e.getKey()) && terminal;

            return new ActorRunState(machineName, stateName, terminal, canStart);
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
}