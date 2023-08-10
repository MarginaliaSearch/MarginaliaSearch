package nu.marginalia.control.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.process.ProcessService;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mqsm.StateFactory;

@Singleton
public class CrawlerMonitorActor extends AbstractProcessSpawnerActor {

    @Inject
    public CrawlerMonitorActor(StateFactory stateFactory,
                               MqPersistence persistence,
                               ProcessService processService) {
        super(stateFactory,
                persistence,
                processService,
                ProcessInboxNames.CRAWLER_INBOX,
                ProcessService.ProcessId.CRAWLER);
    }


}
