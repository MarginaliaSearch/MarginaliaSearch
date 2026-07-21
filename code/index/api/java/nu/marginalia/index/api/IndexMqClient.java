package nu.marginalia.index.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mqapi.ranking.CreateRankingsRequest;
import nu.marginalia.mqapi.ranking.RankingsName;
import nu.marginalia.service.ServiceId;

import java.util.UUID;

@Singleton
public class IndexMqClient {

    private final MessageQueueFactory messageQueueFactory;

    MqOutbox outbox;

    @Inject
    public IndexMqClient(MessageQueueFactory messageQueueFactory,
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
                ProcessInboxNames.RANKING_CONSTRUCTOR_INBOX + ":" + node,
                CreateRankingsRequest.class.getSimpleName(),
                GsonFactory.get().toJson(new CreateRankingsRequest(RankingsName.SECONDARY))
        );
    }
}
