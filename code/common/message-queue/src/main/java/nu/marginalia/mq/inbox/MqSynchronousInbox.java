package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Message queue inbox that responds to a single message at a time
 * within the polling thread
 */
public class MqSynchronousInbox implements MqInboxIf {
    private final Logger logger = LoggerFactory.getLogger(MqSynchronousInbox.class);

    private final String inboxName;
    private final String instanceUUID;
    private final MqPersistence persistence;

    private volatile boolean run = true;

    private final int pollIntervalMs = Integer.getInteger("mq.inbox.poll-interval-ms", 100);
    private final List<MqSubscription> eventSubscribers = new ArrayList<>();

    private Thread pollDbThread;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public MqSynchronousInbox(MqPersistence persistence,
                              String inboxName,
                              UUID instanceUUID)
    {
        this.persistence = persistence;
        this.inboxName = inboxName;
        this.instanceUUID = instanceUUID.toString();
    }

    /** Subscribe to messages on this inbox. Must be run before start()! */
    @Override
    public void subscribe(MqSubscription subscription) {
        eventSubscribers.add(subscription);
    }

    /** Start receiving messages. <p>
     * <b>Note:</b> Subscribe to messages before calling this method.
     * </p> */
    @Override
    public void start() {
        run = true;

        if (eventSubscribers.isEmpty()) {
            logger.error("No subscribers for inbox {}, registering shredder", inboxName);
        }

        // Add a final handler that fails any message that is not handled
        eventSubscribers.add(new MqInboxShredder());

        pollDbThread = new Thread(this::pollDb, "mq-inbox-update-thread:"+inboxName);
        pollDbThread.setDaemon(true);
        pollDbThread.start();
    }

    /** Stop receiving messages and shut down all threads */
    @Override
    public void stop() throws InterruptedException {
        if (!run)
            return;

        logger.info("Shutting down inbox {}", inboxName);

        run = false;
        pollDbThread.join();
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

    }

    private void handleMessageWithSubscriber(MqSubscription subscriber, MqMessage msg) {

        if (msg.expectsResponse()) {
            respondToMessage(subscriber, msg);
        }
        else {
            acknowledgeNotification(subscriber, msg);
        }
    }

    private void respondToMessage(MqSubscription subscriber, MqMessage msg) {
        try {
            final var rsp = subscriber.onRequest(msg);
            sendResponse(msg, rsp.state(), rsp.message());
        } catch (Exception ex) {
            logger.error("Message Queue subscriber threw exception", ex);
            sendResponse(msg, MqMessageState.ERR);
        }
    }

    private void acknowledgeNotification(MqSubscription subscriber, MqMessage msg) {
        try {
            subscriber.onNotification(msg);
            updateMessageState(msg, MqMessageState.OK);
        }
        catch (Exception ex) {
            logger.error("Message Queue subscriber threw exception", ex);
            updateMessageState(msg, MqMessageState.ERR);
        }
    }

    private void sendResponse(MqMessage msg, MqMessageState state) {
        try {
            persistence.updateMessageState(msg.msgId(), state);
        }
        catch (SQLException ex) {
            logger.error("Failed to update message state", ex);
        }
    }

    private void updateMessageState(MqMessage msg, MqMessageState state) {
        try {
            persistence.updateMessageState(msg.msgId(), state);
        }
        catch (SQLException ex2) {
            logger.error("Failed to update message state", ex2);
        }
    }

    private void sendResponse(MqMessage msg, MqMessageState mqMessageState, String response) {
        try {
            persistence.sendResponse(msg.msgId(), mqMessageState, response);
        }
        catch (SQLException ex) {
            logger.error("Failed to update message state", ex);
        }
    }

    private volatile java.util.concurrent.Future<?> currentTask = null;
    public void pollDb() {
        try {
            for (long tick = 1; run; tick++) {

                var messages = pollInbox(tick);

                for (var msg : messages) {
                    // Handle message in a separate thread but wait for that thread, so we can interrupt that thread
                    // without interrupting the polling thread and shutting down the inbox completely
                    try {
                        currentTask = executorService.submit(() -> handleMessage(msg));
                        currentTask.get();
                    }
                    catch (Exception ex) {
                        logger.error("Inbox task was aborted", ex);
                    }
                    finally {
                        currentTask = null;
                    }
                }

                if (messages.isEmpty()) {
                    TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
                }
            }
        }
        catch (InterruptedException ex) {
            logger.error("MQ inbox update thread interrupted", ex);
        }
    }

    /** Attempt to abort the current task using an interrupt */
    public void abortCurrentTask() {
        var task = currentTask; // capture the value to avoid race conditions with the
                                // polling thread between the check and the interrupt
        if (task != null) {
            task.cancel(true);
        }
    }


    private void handleMessage(MqMessage msg) {
        logger.info("Notifying subscribers of msg {}", msg.msgId());

        boolean handled = false;

        for (var eventSubscriber : eventSubscribers) {
            if (eventSubscriber.filter(msg)) {
                handleMessageWithSubscriber(eventSubscriber, msg);
                handled = true;
                break;
            }
        }

        if (!handled) {
            logger.error("No subscriber wanted to handle msg {}", msg.msgId());
        }
    }

    private Collection<MqMessage> pollInbox(long tick) {
        try {
            return persistence.pollInbox(inboxName, instanceUUID, tick, 1);
        }
        catch (SQLException ex) {
            logger.error("Failed to poll inbox", ex);
            return List.of();
        }
    }

    /** Retrieve the last N messages from the inbox. */
    @Override
    public List<MqMessage> replay(int lastN) {
        try {
            return persistence.lastNMessages(inboxName, lastN);
        }
        catch (SQLException ex) {
            logger.error("Failed to replay inbox", ex);
            return List.of();
        }
    }

}
