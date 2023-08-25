package nu.marginalia.control.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.control.process.ProcessService;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.ProcessInboxNames;

@Singleton
public class IndexConstructorMonitorActor extends AbstractProcessSpawnerActor {


    @Inject
    public IndexConstructorMonitorActor(ActorStateFactory stateFactory,
                                        MqPersistence persistence,
                                        ProcessService processService) {
        super(stateFactory, persistence, processService, ProcessInboxNames.INDEX_CONSTRUCTOR_INBOX, ProcessService.ProcessId.INDEX_CONSTRUCTOR);
    }


}
