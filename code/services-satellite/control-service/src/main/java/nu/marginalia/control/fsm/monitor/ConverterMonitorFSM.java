package nu.marginalia.control.fsm.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.converting.mqapi.ConverterInboxNames;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateFactory;

@Singleton
public class ConverterMonitorFSM extends AbstractProcessSpawnerFSM {


    @Inject
    public ConverterMonitorFSM(StateFactory stateFactory,
                               MqPersistence persistence,
                               ProcessService processService) {
        super(stateFactory, persistence, processService, ConverterInboxNames.CONVERTER_INBOX, ProcessService.ProcessId.CONVERTER);
    }


}
