package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessage;

public interface MqSubscription {
    boolean filter(MqMessage rawMessage);

    MqInboxResponse handle(MqMessage msg);
}
