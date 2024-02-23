package nu.marginalia.actor;

import com.google.gson.Gson;
import com.google.inject.Singleton;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.model.gson.GsonFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class ExecutorActorStateMachines {
    private final Gson gson = GsonFactory.get();
    public Map<ExecutorActor, ActorStateMachine> stateMachines = new HashMap<>();

    public ActorStateMachine get(ExecutorActor actor) {
        return stateMachines.get(actor);
    }

    void put(ExecutorActor actor, ActorStateMachine definition) {
        stateMachines.put(actor, definition);
    }

    public Map<ExecutorActor, ActorStateInstance> getActorStates() {
        return stateMachines.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().getState())
        );
    }

    public void init(ExecutorActor process) throws Exception {
        stateMachines.get(process).init();
    }

    public void initFrom(ExecutorActor process, ActorStep step) throws Exception {
        stateMachines.get(process).initFrom(
                step.getClass().getSimpleName().toUpperCase(),
                gson.toJson(step)
        );
    }

    public void startFromJSON(ExecutorActor process, String state, String json) throws Exception {
        if (json.isBlank()) {
            stateMachines.get(process).initFrom(state);
        }
        else {
            stateMachines.get(process).initFrom(state, json);
        }
    }

    public void stop(ExecutorActor process) throws Exception {
        stateMachines.get(process).abortExecution();
    }
}
