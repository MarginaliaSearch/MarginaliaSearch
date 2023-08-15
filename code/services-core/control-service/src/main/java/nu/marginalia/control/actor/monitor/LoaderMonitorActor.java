package nu.marginalia.control.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.control.process.ProcessService;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mq.persistence.MqPersistence;

@Singleton
public class LoaderMonitorActor extends AbstractProcessSpawnerActor {


    @Inject
    public LoaderMonitorActor(ActorStateFactory stateFactory,
                              MqPersistence persistence,
                              ProcessService processService) {

        super(stateFactory, persistence, processService,
                ProcessInboxNames.LOADER_INBOX,
                ProcessService.ProcessId.LOADER);
    }

}
