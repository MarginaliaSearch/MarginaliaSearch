package nu.marginalia.control.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateMachine;
import nu.marginalia.mqsm.graph.AbstractStateGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public class ControlProcesses {
    private final MqPersistence persistence;
    public Map<String, StateMachine> stateMachines = new HashMap<>();

    @Inject
    public ControlProcesses(MqPersistence persistence,
                            RepartitionReindexProcess repartitionReindexProcess
                            ) {
        this.persistence = persistence;

        register("REPARTITION-REINDEX", repartitionReindexProcess);
    }

    private void register(String name, AbstractStateGraph graph) {
        stateMachines.put(name, new StateMachine(persistence, name, UUID.randomUUID(), graph));
    }

    public void start(String name) throws Exception {
        stateMachines.get(name).init();
    }

    public void resume(String name) throws Exception {
        stateMachines.get(name).resume();
    }
}
