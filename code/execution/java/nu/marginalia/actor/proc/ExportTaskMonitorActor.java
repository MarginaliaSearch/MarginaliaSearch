package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.monitor.AbstractProcessSpawnerActor;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.process.ProcessService;
import nu.marginalia.service.module.ServiceConfiguration;

@Singleton
public class ExportTaskMonitorActor extends AbstractProcessSpawnerActor {

    @Inject
    public ExportTaskMonitorActor(Gson gson,
                                  ServiceConfiguration configuration,
                                  MqPersistence persistence,
                                  ProcessService processService) {
        super(gson,
                configuration,
                persistence,
                processService,
                ProcessInboxNames.EXPORT_TASK_INBOX,
                ProcessService.ProcessId.EXPORT_TASKS);
    }


}
