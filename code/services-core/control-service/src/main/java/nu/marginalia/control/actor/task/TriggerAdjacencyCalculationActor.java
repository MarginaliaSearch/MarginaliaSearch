package nu.marginalia.control.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.control.process.ProcessService;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorResumeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class TriggerAdjacencyCalculationActor extends AbstractActorPrototype {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String END = "END";
    private final ProcessService processService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public TriggerAdjacencyCalculationActor(ActorStateFactory stateFactory,
                                            ProcessService processService) {
        super(stateFactory);
        this.processService = processService;
    }

    @Override
    public String describe() {
        return "Calculate website similarities";
    }

    @ActorState(name = INITIAL, next = END,
                resume = ActorResumeBehavior.ERROR,
                description = """
                        Spawns a WebsitesAdjacenciesCalculator process and waits for it to finish.
                        """
    )
    public void init(Integer unused) throws Exception {
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
            error("Error triggering adjacency calculation");
        }
    }

}
