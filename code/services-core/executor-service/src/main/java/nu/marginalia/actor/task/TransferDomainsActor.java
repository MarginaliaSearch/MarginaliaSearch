package nu.marginalia.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.client.Context;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

@Singleton
public class TransferDomainsActor extends AbstractActorPrototype {


    // STATES
    public static final String INITIAL = "INITIAL";
    public static final String TRANSFER_DOMAINS = "TRANSFER-DOMAINS";
    public static final String UPDATE_DONOR_LOG = "UPDATE_DONOR_LOG";

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
                next = TRANSFER_DOMAINS,
                description = """
                    Ensure preconditions are met
                    """)
    public Message init(Message message) throws Exception {
        var storages = storageService.getActiveFileStorages(FileStorageType.CRAWL_DATA);

        // Ensure crawl data exists to receive into
        if (storages.isEmpty()) {
            var storage = storageService.allocateTemporaryStorage(
                    storageService.getStorageBase(FileStorageBaseType.STORAGE),
                    FileStorageType.CRAWL_DATA,
                    "crawl-data",
                    "Crawl Data"
            );
            storageService.enableFileStorage(storage.id());

        }

        return message;
    }

    @ActorState(name = TRANSFER_DOMAINS,
                next = UPDATE_DONOR_LOG,
                resume = ActorResumeBehavior.ERROR,
                description = """
                        Do the needful
                        """
    )
    public Message transferData(Message message) throws Exception {
        var storageId = storageService.getActiveFileStorages(FileStorageType.CRAWL_DATA).get(0);
        var storage = storageService.getStorage(storageId);

        var spec = executorClient.getTransferSpec(Context.internal(), message.sourceNode, message.count);
        if (spec.size() == 0) {
            transition("END", "NOTHING TO TRANSFER");
        }

        Path basePath = storage.asPath();
        try (var workLog = new WorkLog(basePath.resolve("crawler.log"));
             var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("UPDATE EC_DOMAIN SET NODE_AFFINITY=? WHERE ID=?");
        ) {
            for (var item : spec.items()) {
                logger.info("{}", item);
                logger.info("Transferring {}", item.domainName());

                Path dest = basePath.resolve(item.path());
                Files.createDirectories(dest.getParent());
                try (var fileStream = Files.newOutputStream(dest)) {
                    executorClient.transferFile(Context.internal(),
                            message.sourceNode,
                            item.fileStorageId(),
                            item.path(),
                            fileStream);

                    stmt.setInt(1, nodeId);
                    stmt.setInt(2, item.domainId());
                    stmt.executeUpdate();

                    executorClient.yieldDomain(Context.internal(), message.sourceNode, item);
                    workLog.setJobToFinished(item.domainName(), item.path(), 1);
                }
                catch (IOException ex) {
                    Files.deleteIfExists(dest);
                    error(ex);
                }
                catch (Exception ex) {
                    error(ex);
                }
            }
        }

        return message;
    }

    @ActorState(name = UPDATE_DONOR_LOG,
            next = END,
            resume = ActorResumeBehavior.ERROR,
            description = """
                        Do the needful
                        """
    )
    public void updateDonorLog(Message message) throws InterruptedException {
        var outbox = new MqOutbox(persistence, executorServiceName, message.sourceNode,
                getClass().getSimpleName(), nodeId, UUID.randomUUID());

        try {
            outbox.send("PRUNE-CRAWL-DATA", ":-)");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            outbox.stop();
        }
    }
}
