package nu.marginalia.mq;

import nu.marginalia.mq.inbox.MqAsynchronousInbox;
import nu.marginalia.mq.inbox.MqInboxIf;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.mq.inbox.MqSynchronousInbox;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;

@Singleton
public class MessageQueueFactory {
    private final MqPersistence persistence;

    @Inject
    public MessageQueueFactory(MqPersistence persistence) {
        this.persistence = persistence;
    }

    public MqSingleShotInbox createSingleShotInbox(String inboxName, UUID instanceUUID)
    {
        return new MqSingleShotInbox(persistence, inboxName, instanceUUID);
    }


    public MqInboxIf createAsynchronousInbox(String inboxName, UUID instanceUUID)
    {
        return new MqAsynchronousInbox(persistence, inboxName, instanceUUID);
    }

    public MqInboxIf createSynchronousInbox(String inboxName, UUID instanceUUID)
    {
        return new MqSynchronousInbox(persistence, inboxName, instanceUUID);
    }


    public MqOutbox createOutbox(String inboxName, String outboxName, UUID instanceUUID)
    {
        return new MqOutbox(persistence, inboxName, outboxName, instanceUUID);
    }
}
