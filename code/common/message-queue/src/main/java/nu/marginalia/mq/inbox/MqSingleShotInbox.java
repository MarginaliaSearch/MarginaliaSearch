package nu.marginalia.mq.inbox;

import lombok.SneakyThrows;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** A single-shot inbox that can be used to wait for a single message
 *  to arrive in an inbox, and then reply to that message
 */
public class MqSingleShotInbox {

    private final String inboxName;
    private final String instanceUUID;
    private final MqPersistence persistence;

    public MqSingleShotInbox(MqPersistence persistence,
                             String inboxName,
                             UUID instanceUUID
                             ) {
        this.inboxName = inboxName;
        this.instanceUUID = instanceUUID.toString();
        this.persistence = persistence;
    }

    /** Wait for a message to arrive in the specified inbox, up to the specified timeout.
     *
     *  @param timeout The timeout
     *  @param unit The time unit
     *  @return The message, or empty if no message arrived before the timeout
     */
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


    /** Steal a message from the inbox, and change the owner to this instance.  This is useful
     * for resuming an aborted process.
     *
     *  @param predicate A predicate that must be true for the message to be stolen
     *  @return The stolen message, or empty if no message was stolen
     */
    @SneakyThrows
    public Optional<MqMessage> stealMessage(Predicate<MqMessage> predicate) {
        for (var message : persistence.eavesdrop(inboxName, 5)) {
            if (predicate.test(message)) {
                persistence.changeOwner(message.msgId(), instanceUUID, -1);
                return Optional.of(message);
            }
        }

        return Optional.empty();
    }

    /** Send a response to the specified message. If the original message has no response inbox,
     * the original message will be marked as OK instead.
     *
     *  @param originalMessage The original message
     *  @param response The response
     */
    public void sendResponse(MqMessage originalMessage, MqInboxResponse response) {
        try {
            if (!originalMessage.expectsResponse()) {
                // If the original message doesn't expect a response, we can just mark it as OK,
                // since the sendResponse method will fail explosively since it can't insert a response
                // to a non-existent inbox.

                persistence.updateMessageState(originalMessage.msgId(), MqMessageState.OK);
            }
            else {
                persistence.sendResponse(originalMessage.msgId(), response.state(), response.message());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
