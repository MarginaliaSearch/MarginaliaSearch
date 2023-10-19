package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import spark.Request;
import spark.Response;

public class SideloadService {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public SideloadService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public Object sideloadDirtree(Request request, Response response) throws Exception {
         actorControlService.startFrom(ExecutorActor.CONVERT, ConvertActor.CONVERT_DIRTREE, request.queryParams("path"));
         return "";
    }

    public Object sideloadEncyclopedia(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT, ConvertActor.CONVERT_ENCYCLOPEDIA, request.queryParams("path"));
        return "";
    }

    public Object sideloadStackexchange(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT, ConvertActor.CONVERT_STACKEXCHANGE, request.queryParams("path"));
        return "";
    }
}
