package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.persistence.MqPersistence;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** A single-shot inbox that can be used to wait for a single message
 *  to arrive in an inbox, and then reply to that message
 */
public class MqSingleShotInbox {

    private final String inboxName;
    private final String instanceUUID;
    private final MqPersistence persistence;

    public MqSingleShotInbox(String inboxName,
                             String instanceUUID,
                             MqPersistence persistence) {
        this.inboxName = inboxName;
        this.instanceUUID = instanceUUID;
        this.persistence = persistence;
    }

    public Optional<MqMessage> waitForMessage(long timeout, TimeUnit unit) throws InterruptedException, SQLException {
        final long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

        for (int i = 0;; i++) {
            if (System.currentTimeMillis() >= deadline) {
                return Optional.empty();
            }

            var messages = persistence.pollInbox(inboxName, instanceUUID, i, 1);

            if (messages.size() > 0) {
                return Optional.of(messages.iterator().next());
            }

            TimeUnit.SECONDS.sleep(1);
        }
    }

    public void sendResponse(MqMessage originalMessage, MqInboxResponse response) {
        try {
            persistence.sendResponse(originalMessage.msgId(), response.state(), response.message());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
