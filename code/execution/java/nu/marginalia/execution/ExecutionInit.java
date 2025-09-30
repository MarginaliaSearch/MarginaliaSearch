package nu.marginalia.execution;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;

@Singleton
public class ExecutionInit {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public ExecutionInit(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public void initDefaultActors() throws Exception {
        actorControlService.start(ExecutorActor.MONITOR_PROCESS_LIVENESS);
        actorControlService.start(ExecutorActor.MONITOR_FILE_STORAGE);
        actorControlService.start(ExecutorActor.PROC_CONVERTER_SPAWNER);
        actorControlService.start(ExecutorActor.PROC_CRAWLER_SPAWNER);
        actorControlService.start(ExecutorActor.PROC_INDEX_CONSTRUCTOR_SPAWNER);
        actorControlService.start(ExecutorActor.PROC_LOADER_SPAWNER);
        actorControlService.start(ExecutorActor.PROC_EXPORT_TASKS_SPAWNER);
        actorControlService.stop(ExecutorActor.PROC_NDP_SPAWNER);
        actorControlService.stop(ExecutorActor.PROC_PING_SPAWNER);
    }
}
