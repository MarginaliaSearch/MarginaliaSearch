package nu.marginalia.control.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateFactory;

@Singleton
public class LoaderMonitorActor extends AbstractProcessSpawnerActor {


    @Inject
    public LoaderMonitorActor(StateFactory stateFactory,
                              MqPersistence persistence,
                              ProcessService processService) {

        super(stateFactory, persistence, processService,
                ProcessInboxNames.LOADER_INBOX,
                ProcessService.ProcessId.LOADER);
    }

}
