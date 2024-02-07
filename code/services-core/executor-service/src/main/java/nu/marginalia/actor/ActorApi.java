package nu.marginalia.actor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.executor.api.RpcActorRunState;
import nu.marginalia.executor.api.RpcActorRunStates;
import nu.marginalia.executor.api.RpcFsmName;
import nu.marginalia.executor.api.RpcProcessId;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.process.ProcessService;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.Comparator;

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


    public void startActor(RpcFsmName actorName) throws Exception {
        ExecutorActor actor = translateActor(actorName.getActorName());

        actors.start(actor);
    }

    public void stopActor(RpcFsmName actorName) {
        ExecutorActor actor = translateActor(actorName.getActorName());
        actors.stop(actor);
    }

    public Object stopProcess(RpcProcessId processId) {
        ProcessService.ProcessId id = ProcessService.translateExternalIdBase(processId.getProcessId());

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


    public RpcActorRunStates getActorStates() {
        var items = actors.getActorStates().entrySet().stream().map(e -> {
                    final var stateGraph = actors.getActorDefinition(e.getKey());

                    final ActorStateInstance state = e.getValue();
                    final String actorDescription = stateGraph.describe();

                    final String machineName = e.getKey().name();
                    final String stateName = state.name();

                    final String stateDescription = "";

                    final boolean terminal = state.isFinal();
                    final boolean canStart = actors.isDirectlyInitializable(e.getKey()) && terminal;

                    return RpcActorRunState
                            .newBuilder()
                            .setActorName(machineName)
                            .setState(stateName)
                            .setActorDescription(actorDescription)
                            .setStateDescription(stateDescription)
                            .setTerminal(terminal)
                            .setCanStart(canStart)
                            .build();

                })
                .filter(s -> !s.getTerminal() || s.getCanStart())
                .sorted(Comparator.comparing(RpcActorRunState::getActorName))
                .toList();

        return RpcActorRunStates.newBuilder()
                .setNode(serviceConfiguration.node())
                .addAllActorRunStates(items)
                .build();

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
