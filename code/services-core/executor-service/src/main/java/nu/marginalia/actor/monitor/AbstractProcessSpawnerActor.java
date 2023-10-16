package nu.marginalia.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorTerminalState;
import nu.marginalia.process.ProcessService;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class AbstractProcessSpawnerActor extends AbstractActorPrototype {

    private final MqPersistence persistence;
    private final ProcessService processService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String INITIAL = "INITIAL";
    public static final String MONITOR = "MONITOR";
    public static final String RUN = "RUN";
    public static final String ERROR = "ERROR";
    public static final String ABORTED = "ABORTED";
    public static final String END = "END";

    public static final int MAX_ATTEMPTS = 3;
    private final String inboxName;
    private final ProcessService.ProcessId processId;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final int node;

    public String describe() {
        return "Spawns a(n) " + processId +  " process and monitors its inbox for messages";
    }

    @Inject
    public AbstractProcessSpawnerActor(ActorStateFactory stateFactory,
                                       ServiceConfiguration configuration,
                                       MqPersistence persistence,
                                       ProcessService processService,
                                       String inboxName,
                                       ProcessService.ProcessId processId) {
        super(stateFactory);
        this.node = configuration.node();
        this.persistence = persistence;
        this.processService = processService;
        this.inboxName = inboxName + ":" + node;
        this.processId = processId;
    }

    @ActorState(name = INITIAL, next = MONITOR)
    public void init() {

    }

    @ActorState(name = MONITOR,
                next = MONITOR,
                resume = ActorResumeBehavior.RETRY,
                transitions = {MONITOR, RUN},
                description = """
                        Monitors the inbox of the process for messages.
                        If a message is found, transition to RUN.
                        The state takes an optional Integer parameter errorAttempts
                        that is passed to run. errorAttempts is set to zero after
                        a few seconds of silence.
                        """
    )
    public void monitor(Integer errorAttempts) throws SQLException, InterruptedException {

        if (errorAttempts == null) {
            errorAttempts = 0;
        }
        for (;;) {
            var messages = persistence.eavesdrop(inboxName, 1);

            if (messages.isEmpty() && !processService.isRunning(processId)) {
                TimeUnit.SECONDS.sleep(5);

                if (errorAttempts > 0) { // Reset the error counter if there is silence in the inbox
                    transition(MONITOR, 0);
                }
                // else continue
            } else {
                transition(RUN, errorAttempts);
            }
        }
    }

    @ActorState(name = RUN,
                resume = ActorResumeBehavior.RESTART,
                transitions = {MONITOR, ERROR, RUN, ABORTED},
                description = """
                        Runs the process.
                        If the process fails, retransition to RUN up to MAX_ATTEMPTS times.
                        After MAX_ATTEMPTS at restarting the process, transition to ERROR.
                        If the process is cancelled, transition to ABORTED.
                        If the process is successful, transition to MONITOR(errorAttempts).
                        """
    )
    public void run(Integer attempts) throws Exception {
        if (attempts == null)
            attempts = 0;

        try {
            long startTime = System.currentTimeMillis();
            var exec = new TaskExecution();
            long endTime = System.currentTimeMillis();

            if (exec.isError()) {
                if (attempts < MAX_ATTEMPTS) transition(RUN, attempts + 1);
                else error();
            }
            else if (endTime - startTime < TimeUnit.SECONDS.toMillis(1)) {
                // To avoid boot loops, we transition to error if the process
                // didn't run for longer than 1 seconds.  This might happen if
                // the process crashes before it can reach the heartbeat and inbox
                // stages of execution.  In this case it would not report having acted
                // on its message, and the process would be restarted forever without
                // the attempts counter incrementing.
                error("Process terminated within 1 seconds of starting");
            }
        }
        catch (InterruptedException ex) {
            // We get this exception when the process is cancelled by the user

            processService.kill(processId);
            setCurrentMessageToDead();

            transition(ABORTED);
        }

        transition(MONITOR, attempts);
    }

    /** Sets the message to dead in the database to avoid
     * the service respawning on the same task when we
     * re-enable this actor */
    private void setCurrentMessageToDead() {
        try {
            var messages = persistence.eavesdrop(inboxName, 1);

            if (messages.isEmpty()) // Possibly a race condition where the task is already finished
                return;

            var theMessage = messages.iterator().next();
            persistence.updateMessageState(theMessage.msgId(), MqMessageState.DEAD);
        }
        catch (SQLException ex) {
            logger.error("Tried but failed to set the message for " + processId + " to dead", ex);
        }
    }

    @ActorTerminalState(name = ABORTED, description = "The process was manually aborted")
    public void aborted() throws Exception {}


    /** Encapsulates the execution of the process in a separate thread so that
     * we can interrupt the thread if the process is cancelled */
    private class TaskExecution {
        private final AtomicBoolean error = new AtomicBoolean(false);
        public TaskExecution() throws ExecutionException, InterruptedException {
            // Run this call in a separate thread so that this thread can be interrupted waiting for it
            executorService.submit(() -> {
                try {
                    processService.trigger(processId);
                } catch (Exception e) {
                    logger.warn("Error in triggering process", e);
                    error.set(true);
                }
            }).get(); // Wait for the process to start
        }

        public boolean isError() {
            return error.get();
        }
    }
}
