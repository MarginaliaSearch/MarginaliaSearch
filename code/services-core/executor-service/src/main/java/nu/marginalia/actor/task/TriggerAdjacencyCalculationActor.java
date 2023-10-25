package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.process.ProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class TriggerAdjacencyCalculationActor extends RecordActorPrototype {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ProcessService processService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public record Run() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Run() -> {
                AtomicBoolean hasError = new AtomicBoolean(false);
                var future = executor.submit(() -> {
                    try {
                        processService.trigger(ProcessService.ProcessId.ADJACENCIES_CALCULATOR, "load");
                    }
                    catch (Exception ex) {
                        logger.warn("Error triggering adjacency calculation", ex);
                        hasError.set(true);
                    }
                });
                future.get();

                if (hasError.get()) {
                    yield new Error("Error triggering adjacency calculation");
                }
                yield new End();
            }
            default -> new Error();
        };
    }

    @Inject
    public TriggerAdjacencyCalculationActor(Gson gson,
                                            ProcessService processService) {
        super(gson);
        this.processService = processService;
    }

    @Override
    public String describe() {
        return "Calculate website similarities";
    }

}
