package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.actor.Actor;
import nu.marginalia.actor.ActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import spark.Request;
import spark.Response;

public class SideloadService {
    private final ActorControlService actorControlService;

    @Inject
    public SideloadService(ActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public Object sideloadDirtree(Request request, Response response) throws Exception {
         actorControlService.startFrom(Actor.CONVERT, ConvertActor.CONVERT_DIRTREE, request.queryParams("path"));
         return "";
    }

    public Object sideloadEncyclopedia(Request request, Response response) throws Exception {
        actorControlService.startFrom(Actor.CONVERT, ConvertActor.CONVERT_ENCYCLOPEDIA, request.queryParams("path"));
        return "";
    }

    public Object sideloadStackexchange(Request request, Response response) throws Exception {
        actorControlService.startFrom(Actor.CONVERT, ConvertActor.CONVERT_STACKEXCHANGE, request.queryParams("path"));
        return "";
    }
}
