package nu.marginalia.control.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class TriggerAdjacencyCalculationActor extends AbstractStateGraph {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String END = "END";
    private final ProcessService processService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public TriggerAdjacencyCalculationActor(StateFactory stateFactory,
                                            ProcessService processService) {
        super(stateFactory);
        this.processService = processService;
    }

    @GraphState(name = INITIAL, next = END,
                resume = ResumeBehavior.ERROR,
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
