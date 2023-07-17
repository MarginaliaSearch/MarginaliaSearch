package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.service.server.BaseServiceParams;

@Singleton
public class ProcessOutboxFactory {
    private final BaseServiceParams params;
    private final MqPersistence persistence;

    @Inject
    public ProcessOutboxFactory(BaseServiceParams params, MqPersistence persistence) {
        this.params = params;
        this.persistence = persistence;
    }

    public MqOutbox createConverterOutbox() {
        return new MqOutbox(persistence, ProcessInboxNames.CONVERTER_INBOX, params.configuration.serviceName(), params.configuration.instanceUuid());
    }
    public MqOutbox createLoaderOutbox() {
        return new MqOutbox(persistence, ProcessInboxNames.LOADER_INBOX, params.configuration.serviceName(), params.configuration.instanceUuid());
    }
}
