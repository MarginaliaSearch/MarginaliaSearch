package nu.marginalia.mq.outbox;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.persistence.MqPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MqOutbox {
    private final Logger logger = LoggerFactory.getLogger(MqOutbox.class);
    private final MqPersistence persistence;
    private final String inboxName;
    private final String replyInboxName;
    private final String instanceUUID;

    private final ConcurrentHashMap<Long, Long> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MqMessage> pendingResponses = new ConcurrentHashMap<>();

    private final int pollIntervalMs = Integer.getInteger("mq.outbox.poll-interval-ms", 100);
    private final Thread pollThread;

    private volatile boolean run = true;

    public MqOutbox(MqPersistence persistence,
                    String inboxName,
                    UUID instanceUUID) {
        this.persistence = persistence;

        this.inboxName = inboxName;
        this.replyInboxName = "reply:" + inboxName;
        this.instanceUUID = instanceUUID.toString();

        pollThread = new Thread(this::poll, "mq-outbox-poll-thread:" + inboxName);
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() throws InterruptedException {
        if (!run)
            return;

        logger.info("Shutting down outbox {}", inboxName);

        pendingRequests.clear();

        run = false;
        pollThread.join();
    }

    private void poll() {
        try {
            for (long id = 1; run; id++) {
                pollDb(id);

                TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
            }
        } catch (InterruptedException ex) {
            logger.error("Outbox poll thread interrupted", ex);
        }
    }

    private void pollDb(long tick) {
        if (pendingRequests.isEmpty())
            return;

        try {
            var updates = persistence.pollReplyInbox(replyInboxName, instanceUUID, tick);

            for (var message : updates) {
                pendingResponses.put(message.relatedId(), message);
                pendingRequests.remove(message.relatedId());
            }

            if (updates.isEmpty() || pendingResponses.isEmpty())
                return;

            logger.info("Notifying {} pending responses", pendingResponses.size());

            synchronized (pendingResponses) {
                pendingResponses.notifyAll();
            }
        }
        catch (SQLException ex) {
            logger.error("Failed to poll inbox", ex);
        }

    }

    public MqMessage send(String function, String payload) throws Exception {
        var id = persistence.sendNewMessage(inboxName, replyInboxName, function, payload, null);
        pendingRequests.put(id, id);

        synchronized (pendingResponses) {
            while (!pendingResponses.containsKey(id)) {
                pendingResponses.wait(100);
            }
            return pendingResponses.remove(id);
        }
    }

    public long notify(String function, String payload) throws Exception {
        return persistence.sendNewMessage(inboxName, null, function, payload, null);
    }

}