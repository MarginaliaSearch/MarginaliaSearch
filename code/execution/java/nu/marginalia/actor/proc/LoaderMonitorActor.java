package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.monitor.AbstractProcessSpawnerActor;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.process.ProcessSpawnerService;
import nu.marginalia.service.module.ServiceConfiguration;

@Singleton
public class LoaderMonitorActor extends AbstractProcessSpawnerActor {


    @Inject
    public LoaderMonitorActor(Gson gson,
                              ServiceConfiguration configuration,
                              MqPersistence persistence,
                              ProcessSpawnerService processSpawnerService) {

        super(gson,
                configuration,
                persistence, processSpawnerService,
                ProcessInboxNames.LOADER_INBOX,
                ProcessSpawnerService.ProcessId.LOADER);
    }

}
