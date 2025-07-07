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
public class NdpMonitorActor extends AbstractProcessSpawnerActor {

    @Inject
    public NdpMonitorActor(Gson gson,
                                   ServiceConfiguration configuration,
                                   MqPersistence persistence,
                                   ProcessSpawnerService processSpawnerService) {
        super(gson,
                configuration,
                persistence,
                processSpawnerService,
                ProcessInboxNames.NDP_INBOX,
                ProcessSpawnerService.ProcessId.NDP);
    }


}
