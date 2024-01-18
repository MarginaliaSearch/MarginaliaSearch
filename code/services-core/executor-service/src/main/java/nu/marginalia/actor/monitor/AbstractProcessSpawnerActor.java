package nu.marginalia.actor.monitor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.*;
import nu.marginalia.mq.persistence.MqMessageHandlerRegistry;
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
public class AbstractProcessSpawnerActor extends RecordActorPrototype {

    private final MqPersistence persistence;
    private final ProcessService processService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final int MAX_ATTEMPTS = 3;
    private final String inboxName;
    private final ProcessService.ProcessId processId;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final int node;

    public record Initial() implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Monitor(int errorAttempts) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RESTART)
    public record Run(int attempts) implements ActorStep {}
    @Terminal
    public record Aborted() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial i -> new Monitor(0);
            case Monitor (int errorAttempts) -> {
                for (;;) {
                    var messages = persistence.eavesdrop(inboxName, 1);

                    if (messages.isEmpty() && !processService.isRunning(processId)) {
                        synchronized (processId) {
                            processId.wait(5000);
                        }

                        if (errorAttempts > 0) { // Reset the error counter if there is silence in the inbox
                            yield new Monitor(0);
                        }
                        // else continue
                    } else {
                        // Special: Associate this thread with the message so that we can get tracking
                        MqMessageHandlerRegistry.register(messages.getFirst().msgId());

                        yield new Run(0);
                    }
                }
            }
            case Run (int attempts) -> {
                try {
                    long startTime = System.currentTimeMillis();
                    var exec = new TaskExecution();
                    long endTime = System.currentTimeMillis();

                    if (exec.isError()) {
                        if (attempts < MAX_ATTEMPTS)
                            yield new Run(attempts + 1);
                        else
                            yield new Error();
                    }
                    else if (endTime - startTime < TimeUnit.SECONDS.toMillis(1)) {
                        // To avoid boot loops, we transition to error if the process
                        // didn't run for longer than 1 seconds.  This might happen if
                        // the process crashes before it can reach the heartbeat and inbox
                        // stages of execution.  In this case it would not report having acted
                        // on its message, and the process would be restarted forever without
                        // the attempts counter incrementing.
                        yield new Error("Process terminated within 1 seconds of starting");
                    }
                }
                catch (InterruptedException ex) {
                    // We get this exception when the process is cancelled by the user

                    processService.kill(processId);
                    setCurrentMessageToDead();

                    yield new Aborted();
                }

                yield new Monitor(attempts);
            }
            default -> new Error();
        };
    }

    public String describe() {
        return "Spawns a(n) " + processId +  " process and monitors its inbox for messages";
    }

    @Inject
    public AbstractProcessSpawnerActor(Gson gson,
                                       ServiceConfiguration configuration,
                                       MqPersistence persistence,
                                       ProcessService processService,
                                       String inboxName,
                                       ProcessService.ProcessId processId) {
        super(gson);
        this.node = configuration.node();
        this.persistence = persistence;
        this.processService = processService;
        this.inboxName = inboxName + ":" + node;
        this.processId = processId;
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
