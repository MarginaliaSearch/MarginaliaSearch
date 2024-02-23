package nu.marginalia.mq;

public enum MqMessageState {
    /** The message is new and has not yet been acknowledged by the recipient */
    NEW,
    /** The message has been acknowledged by the recipient */
    ACK,
    /** The message has been processed successfully by the recipient */
    OK,
    /** The message processing has failed */
    ERR,
    /** The message did not reach a terminal state within the TTL */
    DEAD;

    public boolean isTerminal() {
        return this == OK || this == ERR || this == DEAD;
    }
}
