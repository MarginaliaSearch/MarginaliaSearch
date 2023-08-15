package nu.marginalia.control.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.control.process.ProcessService;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.ProcessInboxNames;

@Singleton
public class CrawlerMonitorActor extends AbstractProcessSpawnerActor {

    @Inject
    public CrawlerMonitorActor(ActorStateFactory stateFactory,
                               MqPersistence persistence,
                               ProcessService processService) {
        super(stateFactory,
                persistence,
                processService,
                ProcessInboxNames.CRAWLER_INBOX,
                ProcessService.ProcessId.CRAWLER);
    }


}
