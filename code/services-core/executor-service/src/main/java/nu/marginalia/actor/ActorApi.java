package nu.marginalia.actor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.proc.ProcessLivenessMonitorActor;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.process.ProcessService;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

@Singleton
public class ActorApi {
    private final ExecutorActorControlService actors;
    private final ProcessService processService;
    private final MqPersistence mqPersistence;
    private final ServiceConfiguration serviceConfiguration;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    public ActorApi(ExecutorActorControlService actors,
                    ProcessService processService,
                    MqPersistence mqPersistence,
                    ServiceConfiguration serviceConfiguration)
    {
        this.actors = actors;
        this.processService = processService;
        this.mqPersistence = mqPersistence;
        this.serviceConfiguration = serviceConfiguration;
    }

    public Object startActorFromState(Request request, Response response) throws Exception {
        ExecutorActor actor = translateActor(request.params("id"));
        String state = request.params("state");

        actors.startFromJSON(actor, state, request.body());

        return "";
    }

    public Object startActor(Request request, Response response) throws Exception {
        ExecutorActor actor = translateActor(request.params("id"));

        actors.start(actor);

        return "";
    }

    public Object stopActor(Request request, Response response) {
        ExecutorActor actor = translateActor(request.params("id"));

        actors.stop(actor);

        return "OK";
    }

    public Object stopProcess(Request request, Response response) {
        ProcessService.ProcessId id = ProcessService.translateExternalIdBase(request.params("id"));

        try {
            String inbox = id.name().toLowerCase() + ":" + serviceConfiguration.node();
            var lastMessages = mqPersistence.eavesdrop(inbox, 1);

            // If there are any messages in the inbox, we mark them as dead to prevent
            // the process spawner from reviving the process immediately

            if (null != lastMessages && !lastMessages.isEmpty()) {
                var lastMessage = lastMessages.getFirst();

                if (lastMessage.state() == MqMessageState.ACK) {
                    mqPersistence.updateMessageState(lastMessages.getFirst().msgId(), MqMessageState.DEAD);
                }

            }
            processService.kill(id);
        }
        catch (Exception ex) {
            logger.error("Failed to stop process {}", id, ex);
        }

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
