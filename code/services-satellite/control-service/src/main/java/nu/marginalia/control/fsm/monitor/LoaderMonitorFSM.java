package nu.marginalia.control.fsm.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.converting.mqapi.ConverterInboxNames;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateFactory;

@Singleton
public class LoaderMonitorFSM extends AbstractProcessSpawnerFSM {


    @Inject
    public LoaderMonitorFSM(StateFactory stateFactory,
                            MqPersistence persistence,
                            ProcessService processService) {

        super(stateFactory, persistence, processService,
                ConverterInboxNames.LOADER_INBOX,
                ProcessService.ProcessId.LOADER);
    }

}
