package nu.marginalia.actor;

import nu.marginalia.mq.MqMessage;

/**
 * ExpectedMessage guards against spurious state changes being triggered by old messages in the queue
 * <p>
 * It contains the message id of the last message that was processed, and the messages sent by the state machine to
 * itself via the message queue all have relatedId set to expectedMessageId.  If the state machine is unitialized or
 * in a terminal state, it will accept messages with relatedIds that are equal to -1.
 */
class ExpectedMessage {
    public final long id;

    ExpectedMessage(long id) {
        this.id = id;
    }

    public static ExpectedMessage expectThis(MqMessage message) {
        return new ExpectedMessage(message.relatedId());
    }

    public static ExpectedMessage responseTo(MqMessage message) {
        return new ExpectedMessage(message.msgId());
    }

    public static ExpectedMessage anyUnrelated() {
        return new ExpectedMessage(-1);
    }

    public static ExpectedMessage expectId(long id) {
        return new ExpectedMessage(id);
    }

    public boolean isExpected(MqMessage message) {
        if (id < 0)
            return true;

        return id == message.relatedId();
    }
}
