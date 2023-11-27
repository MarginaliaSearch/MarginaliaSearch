package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessage;

public interface MqSubscription {
    /** Return true if this subscription should handle the message. */
    boolean filter(MqMessage rawMessage);

    /** Handle the message and return a response. */
    MqInboxResponse onRequest(MqMessage msg);

}
