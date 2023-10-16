package nu.marginalia.actor.proc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.monitor.AbstractProcessSpawnerActor;
import nu.marginalia.process.ProcessService;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.service.module.ServiceConfiguration;

@Singleton
public class LoaderMonitorActor extends AbstractProcessSpawnerActor {


    @Inject
    public LoaderMonitorActor(ActorStateFactory stateFactory,
                              ServiceConfiguration configuration,
                              MqPersistence persistence,
                              ProcessService processService) {

        super(stateFactory,
                configuration,
                persistence, processService,
                ProcessInboxNames.LOADER_INBOX,
                ProcessService.ProcessId.LOADER);
    }

}
