package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
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
import java.util.function.Consumer;

public class MqInbox {
    private final Logger logger = LoggerFactory.getLogger(MqInbox.class);

    private final String inboxName;
    private final String instanceUUID;
    private final ExecutorService threadPool;
    private final MqPersistence persistence;

    private volatile boolean run = true;

    private final int pollIntervalMs = Integer.getInteger("mq.inbox.poll-interval-ms", 100);
    private final List<MqSubscription> eventSubscribers = new ArrayList<>();
    private final LinkedBlockingQueue<MqMessage> queue = new LinkedBlockingQueue<>(32);

    private Thread pollDbThread;
    private Thread notifyThread;

    public MqInbox(MqPersistence persistence,
                   String inboxName,
                   UUID instanceUUID)
    {
        this.threadPool = Executors.newCachedThreadPool();
        this.persistence = persistence;
        this.inboxName = inboxName;
        this.instanceUUID = instanceUUID.toString();
    }

    public void subscribe(MqSubscription subscription) {
        eventSubscribers.add(subscription);
    }

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

        if (msg.expectsResponse()) {
            threadPool.execute(() -> respondToMessage(subscriber, msg));
        }
        else {
            threadPool.execute(() -> acknowledgeNotification(subscriber, msg));
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
        } catch (Exception ex) {
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

    public void pollDb() {
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
            return persistence.pollInbox(inboxName, instanceUUID, tick);
        }
        catch (SQLException ex) {
            logger.error("Failed to poll inbox", ex);
            return List.of();
        }
    }

     /** Retrieve the last N messages from the inbox. */
    public List<MqMessage> replay(int lastN) {
        try {
            return persistence.lastNMessages(inboxName, lastN);
        }
        catch (SQLException ex) {
            logger.error("Failed to replay inbox", ex);
            return List.of();
        }
    }


    private class MqInboxShredder implements MqSubscription {

        @Override
        public boolean filter(MqMessage rawMessage) {
            return true;
        }

        @Override
        public MqInboxResponse onRequest(MqMessage msg) {
            logger.warn("Unhandled message {}", msg.msgId());
            return MqInboxResponse.err();
        }

        @Override
        public void onNotification(MqMessage msg) {
            logger.warn("Unhandled message {}", msg.msgId());
        }
    }
}
