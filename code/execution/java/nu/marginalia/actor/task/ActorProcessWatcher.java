package nu.marginalia.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.state.ActorControlFlowException;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.process.ProcessService;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.outbox.MqOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class ActorProcessWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ActorProcessWatcher.class);
    private final MqPersistence persistence;
    private final ProcessService processService;

    @Inject
    public ActorProcessWatcher(MqPersistence persistence,
                               ProcessService processService) {
        this.persistence = persistence;
        this.processService = processService;
    }

    /** Wait for a process to start, and then wait for a response from the process,
     * periodically checking that the process is still running.  If the process dies,
     * and does not respawn, or does not start at all, a control flow exception is thrown
     * that will cause the actor to transition to ERROR.
     * <p>
     * When interrupted, the process is killed and the message is marked as dead.
     */
    public MqMessage waitResponse(MqOutbox outbox, ProcessService.ProcessId processId, long msgId)
            throws ActorControlFlowException, InterruptedException, SQLException
    {
        // enums values only have a single instance,
        // so it's safe to synchronize on them
        // even though it looks a bit weird to
        // synchronize on a parameter like this:
        synchronized (processId) {
            // Wake up the process spawning actor
            processId.notifyAll();
        }

        if (!waitForProcess(processId, TimeUnit.SECONDS, 30)) {
            throw new ActorControlFlowException("Process " + processId + " did not launch");
        }

        for (;;) {
            try {
                // Check for interruption before waiting for response
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

                return outbox.waitResponse(msgId, 1, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex) {
                // Here we mark the message as dead, as it's the user that has aborted the process
                // This will prevent the monitor process from attempting to respawn the process as we kill it

                outbox.flagAsDead(msgId);
                processService.kill(processId);

                logger.info("Process {} killed due to interrupt", processId);
            }
            catch (TimeoutException ex) {
                var state = persistence.getMessage(msgId).state();
                if (state == MqMessageState.ERR || state == MqMessageState.DEAD) {
                    throw new ActorControlFlowException("Process " + processId + " marked message as " + state);
                }

                // Maybe the process died, wait a moment for it to restart
                if (!waitForProcess(processId, TimeUnit.SECONDS, 30)) {

                    // Check if the process has already responded, but we missed it
                    // This infrequently happens if we get unlucky with the timing of the process terminating
                    // and the polling thread...

                    var maybeResponse = outbox.pollResponse(msgId);
                    if (maybeResponse.isPresent()) {
                        return maybeResponse.get();
                    }

                    throw new ActorControlFlowException("Process " + processId + " died and did not re-launch");
                }
            }
        }
    }

    /** Wait the specified time for the specified process to start running (does not start the process) */
    private boolean waitForProcess(ProcessService.ProcessId processId, TimeUnit unit, int duration) throws InterruptedException {

        // Wait for process to start
        long deadline = System.currentTimeMillis() + unit.toMillis(duration);
        while (System.currentTimeMillis() < deadline) {
            if (processService.isRunning(processId))
                return true;

            TimeUnit.MILLISECONDS.sleep(100);
        }

        return false;
    }

}
