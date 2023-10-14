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
    private final ActorControlService actors;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    public ActorApi(ActorControlService actors) {
        this.actors = actors;
    }

    public Object startActorFromState(Request request, Response response) throws Exception {
        Actor actor = translateActor(request.params("id"));
        String state = request.params("state");

        actors.startFromJSON(actor, state, request.body());

        return "";
    }

    public Object startActor(Request request, Response response) throws Exception {
        Actor actor = translateActor(request.params("id"));

        actors.startJSON(actor, request.body());

        return "";
    }

    public Object stopActor(Request request, Response response) {
        Actor actor = translateActor(request.params("id"));

        actors.stop(actor);

        return "OK";
    }

    public Actor translateActor(String name) {
        try {
            return Actor.valueOf(name.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            logger.error("Unknown actor {}", name);
            Spark.halt(400, "Unknown actor name provided");
            return null;
        }
    }
}
