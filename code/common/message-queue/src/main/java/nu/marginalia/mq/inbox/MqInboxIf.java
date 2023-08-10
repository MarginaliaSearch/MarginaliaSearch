package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessage;

import java.util.List;

public interface MqInboxIf {
    void subscribe(MqSubscription subscription);

    void start();

    void stop() throws InterruptedException;

    List<MqMessage> replay(int lastN);
}
