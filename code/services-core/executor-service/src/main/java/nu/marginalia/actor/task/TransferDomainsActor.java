package nu.marginalia.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TransferDomainsActor extends AbstractActorPrototype {
    // STATES
    public static final String INITIAL = "INITIAL";

    public static final String END = "END";
    private final FileStorageService storageService;
    private final ExecutorClient executorClient;
    private final MqPersistence persistence;
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int nodeId;
    private final String executorServiceName;

    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        int sourceNode;
        int count;
    };

    @Override
    public String describe() {
        return "Transfers domains between nodes' crawl data sets";
    }

    @Inject
    public TransferDomainsActor(ActorStateFactory stateFactory,
                                ServiceConfiguration configuration,
                                FileStorageService storageService,
                                ExecutorClient executorClient,
                                MqPersistence persistence,
                                HikariDataSource dataSource)
    {
        super(stateFactory);
        this.storageService = storageService;
        this.executorClient = executorClient;
        this.persistence = persistence;
        this.dataSource = dataSource;
        this.nodeId = configuration.node();
        this.executorServiceName = configuration.serviceName();
    }

    @ActorState(name = INITIAL,
                next = END,
                description = """
                    Transfer the domains
                    """)
    public void init(Message message) throws Exception {

    }


}
