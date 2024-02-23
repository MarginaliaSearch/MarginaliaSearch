package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MqInboxShredder implements MqSubscription {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public MqInboxShredder() {
    }

    @Override
    public boolean filter(MqMessage rawMessage) {
        return true;
    }

    @Override
    public MqInboxResponse onRequest(MqMessage msg) {
        logger.warn("Unhandled message {}", msg.msgId());
        return MqInboxResponse.err();
    }

}
