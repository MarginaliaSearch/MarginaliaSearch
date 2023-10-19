package nu.marginalia.actor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

@Singleton
public class ActorApi {
    private final ExecutorActorControlService actors;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    public ActorApi(ExecutorActorControlService actors) {
        this.actors = actors;
    }

    public Object startActorFromState(Request request, Response response) throws Exception {
        ExecutorActor actor = translateActor(request.params("id"));
        String state = request.params("state");

        actors.startFromJSON(actor, state, request.body());

        return "";
    }

    public Object startActor(Request request, Response response) throws Exception {
        ExecutorActor actor = translateActor(request.params("id"));

        actors.startJSON(actor, request.body());

        return "";
    }

    public Object stopActor(Request request, Response response) {
        ExecutorActor actor = translateActor(request.params("id"));

        actors.stop(actor);

        return "OK";
    }

    public ExecutorActor translateActor(String name) {
        try {
            return ExecutorActor.valueOf(name.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            logger.error("Unknown actor {}", name);
            Spark.halt(400, "Unknown actor name provided");
            return null;
        }
    }
}
