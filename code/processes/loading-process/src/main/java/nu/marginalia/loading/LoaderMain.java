package nu.marginalia.loading;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import lombok.Getter;
import lombok.SneakyThrows;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.ProcessConfigurationModule;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.linkdb.LinkdbWriter;
import nu.marginalia.loading.documents.DocumentLoaderService;
import nu.marginalia.loading.documents.KeywordLoaderService;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.loading.domains.DomainLoaderService;
import nu.marginalia.loading.links.DomainLinksLoaderService;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.service.module.DatabaseModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static nu.marginalia.mqapi.ProcessInboxNames.LOADER_INBOX;

public class LoaderMain {
    private static final Logger logger = LoggerFactory.getLogger(LoaderMain.class);

    private final ProcessHeartbeatImpl heartbeat;
    private final MessageQueueFactory messageQueueFactory;
    private final FileStorageService fileStorageService;
    private final LinkdbWriter linkdbWriter;
    private final LoaderIndexJournalWriter journalWriter;
    private final DomainLoaderService domainService;
    private final DomainLinksLoaderService linksService;
    private final KeywordLoaderService keywordLoaderService;
    private final DocumentLoaderService documentLoaderService;
    private final int node;
    private final Gson gson;

    public static void main(String... args) {
        try {
            new org.mariadb.jdbc.Driver();

            Injector injector = Guice.createInjector(
                    new ProcessConfigurationModule("loader"),
                    new LoaderModule(),
                    new DatabaseModule()
            );

            var instance = injector.getInstance(LoaderMain.class);

            var instructions = instance.fetchInstructions();
            logger.info("Instructions received");
            instance.run(instructions);
        }
        catch (Exception ex) {
            logger.error("Error running loader", ex);
        }
    }

    @Inject
    public LoaderMain(ProcessHeartbeatImpl heartbeat,
                      MessageQueueFactory messageQueueFactory,
                      FileStorageService fileStorageService,
                      LinkdbWriter linkdbWriter,
                      LoaderIndexJournalWriter journalWriter,
                      DomainLoaderService domainService,
                      DomainLinksLoaderService linksService,
                      KeywordLoaderService keywordLoaderService,
                      DocumentLoaderService documentLoaderService,
                      ProcessConfiguration processConfiguration,
                      Gson gson
                      ) {
        this.node = processConfiguration.node();
        this.heartbeat = heartbeat;
        this.messageQueueFactory = messageQueueFactory;
        this.fileStorageService = fileStorageService;
        this.linkdbWriter = linkdbWriter;
        this.journalWriter = journalWriter;
        this.domainService = domainService;
        this.linksService = linksService;
        this.keywordLoaderService = keywordLoaderService;
        this.documentLoaderService = documentLoaderService;
        this.gson = gson;

        heartbeat.start();
    }

    @SneakyThrows
    void run(LoadRequest instructions) {
        LoaderInputData inputData = instructions.getInputData();

        DomainIdRegistry domainIdRegistry = domainService.getOrCreateDomainIds(inputData);

        try {
            var results = ForkJoinPool.commonPool()
                    .invokeAll(
                        List.of(
                            () -> linksService.loadLinks(domainIdRegistry, heartbeat, inputData),
                            () -> keywordLoaderService.loadKeywords(domainIdRegistry, heartbeat, inputData),
                            () -> documentLoaderService.loadDocuments(domainIdRegistry, heartbeat, inputData),
                            () -> domainService.loadDomainMetadata(domainIdRegistry, heartbeat, inputData)
                        )
            );

            for (var result : results) {
                if (result.state() == Future.State.FAILED) {
                    throw result.exceptionNow();
                }
            }

            instructions.ok();
        }
        catch (Exception ex) {
            instructions.err();
            logger.error("Error", ex);
        }
        finally {
            journalWriter.close();
            linkdbWriter.close();
            heartbeat.shutDown();
        }

        System.exit(0);
    }

    private static class LoadRequest {
        @Getter
        private final LoaderInputData inputData;
        private final MqMessage message;
        private final MqSingleShotInbox inbox;

        LoadRequest(LoaderInputData inputData, MqMessage message, MqSingleShotInbox inbox) {
            this.inputData = inputData;
            this.message = message;
            this.inbox = inbox;
        }

        public void ok() {
            inbox.sendResponse(message, MqInboxResponse.ok());
        }
        public void err() {
            inbox.sendResponse(message, MqInboxResponse.err());
        }
    }

    private LoadRequest fetchInstructions() throws Exception {

        var inbox = messageQueueFactory.createSingleShotInbox(LOADER_INBOX, node, UUID.randomUUID());

        var msgOpt = getMessage(inbox, nu.marginalia.mqapi.loading.LoadRequest.class.getSimpleName());
        if (msgOpt.isEmpty())
            throw new RuntimeException("No instruction received in inbox");
        var msg = msgOpt.get();

        if (!nu.marginalia.mqapi.loading.LoadRequest.class.getSimpleName().equals(msg.function())) {
            throw new RuntimeException("Unexpected message in inbox: " + msg);
        }

        try {
            var request = gson.fromJson(msg.payload(), nu.marginalia.mqapi.loading.LoadRequest.class);

            List<Path> inputSources = new ArrayList<>();
            for (var storageId : request.inputProcessDataStorageIds) {
                inputSources.add(fileStorageService.getStorage(storageId).asPath());
            }

            return new LoadRequest(new LoaderInputData(inputSources), msg, inbox);
        }
        catch (Exception ex) {
            inbox.sendResponse(msg, new MqInboxResponse("FAILED", MqMessageState.ERR));
            throw ex;
        }
    }

    private Optional<MqMessage> getMessage(MqSingleShotInbox inbox, String expectedFunction) throws SQLException, InterruptedException {
        var opt = inbox.waitForMessage(30, TimeUnit.SECONDS);
        if (opt.isPresent()) {
            if (!opt.get().function().equals(expectedFunction)) {
                throw new RuntimeException("Unexpected function: " + opt.get().function());
            }
            return opt;
        }
        else {
            var stolenMessage = inbox.stealMessage(msg -> msg.function().equals(expectedFunction));
            stolenMessage.ifPresent(mqMessage -> logger.info("Stole message {}", mqMessage));
            return stolenMessage;
        }
    }

}
