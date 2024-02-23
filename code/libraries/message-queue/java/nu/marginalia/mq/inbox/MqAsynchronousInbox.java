package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqMessageHandlerRegistry;
import nu.marginalia.mq.persistence.MqPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Message queue inbox that spawns news threads for each message */
public class MqAsynchronousInbox implements MqInboxIf {
    private final Logger logger = LoggerFactory.getLogger(MqAsynchronousInbox.class);

    private final String inboxName;
    private final String instanceUUID;
    private final ExecutorService threadPool;
    private final MqPersistence persistence;

    private volatile boolean run = true;

    private final int pollIntervalMs = Integer.getInteger("mq.inbox.poll-interval-ms", 1000);
    private final int maxPollCount = Integer.getInteger("mq.inbox.max-poll-count", 10);
    private final List<MqSubscription> eventSubscribers = new ArrayList<>();
    private final LinkedBlockingQueue<MqMessage> queue = new LinkedBlockingQueue<>(32);

    private Thread pollDbThread;
    private Thread notifyThread;

    public MqAsynchronousInbox(MqPersistence persistence,
                               String inboxName,
                               UUID instanceUUID)
    {
        this(persistence, inboxName, instanceUUID, Executors.newCachedThreadPool());
    }

    public MqAsynchronousInbox(MqPersistence persistence,
                               String inboxName,
                               UUID instanceUUID,
                               ExecutorService executorService)
    {
        this.threadPool = executorService;
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

        notifyThread = new Thread(this::notifySubscribers, "mq-inbox-notify-thread:"+inboxName);
        notifyThread.setDaemon(true);
        notifyThread.start();
    }

    /** Stop receiving messages and shut down all threads */
    @Override
    public void stop() throws InterruptedException {
        if (!run)
            return;

        logger.info("Shutting down inbox {}", inboxName);

        run = false;
        pollDbThread.join();
        notifyThread.join();

        threadPool.shutdownNow();

        while (!threadPool.awaitTermination(5, TimeUnit.SECONDS));
    }

    private void notifySubscribers() {
        try {
            while (run) {

                MqMessage msg = queue.poll(pollIntervalMs, TimeUnit.MILLISECONDS);

                if (msg == null)
                    continue;

                logger.info("Notifying subscribers of message {}", msg.msgId());

                boolean handled = false;

                for (var eventSubscriber : eventSubscribers) {
                    if (eventSubscriber.filter(msg)) {

                        handleMessageWithSubscriber(eventSubscriber, msg);

                        handled = true;
                        break;
                    }
                }

                if (!handled) {
                    logger.error("No subscriber wanted to handle message {}", msg.msgId());
                }
            }
        }
        catch (InterruptedException ex) {
            logger.error("MQ inbox notify thread interrupted", ex);
        }
    }

    private void handleMessageWithSubscriber(MqSubscription subscriber, MqMessage msg) {
        threadPool.execute(() -> respondToMessage(subscriber, msg));
    }

    private void respondToMessage(MqSubscription subscriber, MqMessage msg) {
        try {
            MqMessageHandlerRegistry.register(msg.msgId());
            final var rsp = subscriber.onRequest(msg);

            if (msg.expectsResponse()) {
                sendResponse(msg, rsp.state(), rsp.message());
            }
            else {
                registerResponse(msg, rsp.state());
            }

        } catch (Exception ex) {
            logger.error("Message Queue subscriber threw exception", ex);
            registerResponse(msg, MqMessageState.ERR);
        } finally {
            MqMessageHandlerRegistry.deregister();
        }
    }

    private void registerResponse(MqMessage msg, MqMessageState state) {
        try {
            persistence.updateMessageState(msg.msgId(), state);
        }
        catch (SQLException ex) {
            logger.error("Failed to update message state", ex);
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

    private void pollDb() {
        try {
            for (long tick = 1; run; tick++) {

                queue.addAll(pollInbox(tick));

                TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
            }
        }
        catch (InterruptedException ex) {
            logger.error("MQ inbox update thread interrupted", ex);
        }
    }

     private Collection<MqMessage> pollInbox(long tick) {
        try {
            return persistence.pollInbox(inboxName, instanceUUID, tick, maxPollCount);
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
