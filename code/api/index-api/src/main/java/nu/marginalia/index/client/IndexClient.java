package nu.marginalia.index.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.service.id.ServiceId;

import java.util.UUID;

@Singleton
public class IndexClient {

    private final MessageQueueFactory messageQueueFactory;

    MqOutbox outbox;

    @Inject
    public IndexClient(MessageQueueFactory messageQueueFactory,
                       @Named("wmsa-system-node") Integer nodeId)
    {
        this.messageQueueFactory = messageQueueFactory;

        String inboxName = ServiceId.Index.serviceName;
        String outboxName = "pp:"+System.getProperty("service-name", UUID.randomUUID().toString());
        outbox = messageQueueFactory.createOutbox(inboxName, nodeId, outboxName, nodeId, UUID.randomUUID());
    }

    public MqOutbox outbox() {
        return outbox;
    }

    public long triggerRepartition(int node) throws Exception {
        return messageQueueFactory.sendSingleShotRequest(
                ServiceId.Index.withNode(node),
                IndexMqEndpoints.INDEX_REPARTITION,
                null
        );
    }

    public long triggerRerank(int node) throws Exception {
        return messageQueueFactory.sendSingleShotRequest(
                ServiceId.Index.withNode(node),
                IndexMqEndpoints.INDEX_RERANK,
                null
        );
    }
}
