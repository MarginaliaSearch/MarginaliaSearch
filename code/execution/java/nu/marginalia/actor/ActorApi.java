package nu.marginalia.actor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.functions.execution.api.RpcFsmName;
import nu.marginalia.functions.execution.api.RpcProcessId;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.process.ProcessSpawnerService;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ActorApi {
    private final ExecutorActorControlService actors;
    private final ProcessSpawnerService processSpawnerService;
    private final MqPersistence mqPersistence;
    private final ServiceConfiguration serviceConfiguration;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    public ActorApi(ExecutorActorControlService actors,
                    ProcessSpawnerService processSpawnerService,
                    MqPersistence mqPersistence,
                    ServiceConfiguration serviceConfiguration)
    {
        this.actors = actors;
        this.processSpawnerService = processSpawnerService;
        this.mqPersistence = mqPersistence;
        this.serviceConfiguration = serviceConfiguration;
    }


    public void startActor(RpcFsmName actorName) throws Exception {
        ExecutorActor actor = translateActor(actorName.getActorName());

        actors.start(actor);
    }

    public void stopActor(RpcFsmName actorName) {
        ExecutorActor actor = translateActor(actorName.getActorName());
        actors.stop(actor);
    }

    public Object stopProcess(RpcProcessId processId) {
        ProcessSpawnerService.ProcessId id = ProcessSpawnerService.translateExternalIdBase(processId.getProcessId());

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
            processSpawnerService.kill(id);
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
            throw new IllegalArgumentException("Bad actor provided");
        }
    }
}
