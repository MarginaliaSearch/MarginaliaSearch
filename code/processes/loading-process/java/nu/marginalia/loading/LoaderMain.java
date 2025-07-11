package nu.marginalia.loading;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.loading.documents.DocumentLoaderService;
import nu.marginalia.loading.documents.KeywordLoaderService;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.loading.domains.DomainLoaderService;
import nu.marginalia.loading.links.DomainLinksLoaderService;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.loading.LoadRequest;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import static nu.marginalia.mqapi.ProcessInboxNames.LOADER_INBOX;

public class LoaderMain extends ProcessMainClass {
    private static final Logger logger = LoggerFactory.getLogger(LoaderMain.class);

    private final ProcessHeartbeatImpl heartbeat;
    private final FileStorageService fileStorageService;
    private final DocumentDbWriter documentDbWriter;
    private final DomainLoaderService domainService;
    private final DomainLinksLoaderService linksService;
    private final KeywordLoaderService keywordLoaderService;
    private final DocumentLoaderService documentLoaderService;

    private static boolean insertFoundDomains = Boolean.getBoolean("loader.insertFoundDomains");

    public static void main(String... args) {
        try {
            new org.mariadb.jdbc.Driver();

            Injector injector = Guice.createInjector(
                    new ProcessConfigurationModule("loader"),
                    new LoaderModule(),
                    new DatabaseModule(false)
            );

            var instance = injector.getInstance(LoaderMain.class);

            var instructions = instance.fetchInstructions(LoadRequest.class);
            logger.info("Instructions received");
            instance.run(instructions);
        }
        catch (Throwable ex) {
            logger.error("Error running loader", ex);
        }
    }

    @Inject
    public LoaderMain(ProcessHeartbeatImpl heartbeat,
                      MessageQueueFactory messageQueueFactory,
                      FileStorageService fileStorageService,
                      DocumentDbWriter documentDbWriter,
                      DomainLoaderService domainService,
                      DomainLinksLoaderService linksService,
                      KeywordLoaderService keywordLoaderService,
                      DocumentLoaderService documentLoaderService,
                      ProcessConfiguration processConfiguration,
                      Gson gson
                      ) {

        super(messageQueueFactory, processConfiguration, gson, LOADER_INBOX);

        this.heartbeat = heartbeat;
        this.fileStorageService = fileStorageService;
        this.documentDbWriter = documentDbWriter;
        this.domainService = domainService;
        this.linksService = linksService;
        this.keywordLoaderService = keywordLoaderService;
        this.documentLoaderService = documentLoaderService;

        heartbeat.start();
    }

    void run(Instructions<LoadRequest> instructions) throws Throwable {

        List<Path> inputSources = new ArrayList<>();
        for (var storageId : instructions.value().inputProcessDataStorageIds) {
            inputSources.add(fileStorageService.getStorage(storageId).asPath());
        }
        var inputData = new LoaderInputData(inputSources);

        DomainIdRegistry domainIdRegistry = domainService.getOrCreateDomainIds(heartbeat, inputData);

        try {
            var results = ForkJoinPool.commonPool()
                .invokeAll(List.of());

            if ( true == insertFoundDomains ) {
                results = ForkJoinPool.commonPool()
                        .invokeAll(
                            List.of(
                                () -> linksService.loadLinks(domainIdRegistry, heartbeat, inputData),
                                () -> keywordLoaderService.loadKeywords(domainIdRegistry, heartbeat, inputData),
                                () -> documentLoaderService.loadDocuments(domainIdRegistry, heartbeat, inputData),
                                () -> domainService.loadDomainMetadata(domainIdRegistry, heartbeat, inputData)
                            )
                );
            }
            else {
                results = ForkJoinPool.commonPool()
                        .invokeAll(
                            List.of(
                                () -> keywordLoaderService.loadKeywords(domainIdRegistry, heartbeat, inputData),
                                () -> documentLoaderService.loadDocuments(domainIdRegistry, heartbeat, inputData),
                                () -> domainService.loadDomainMetadata(domainIdRegistry, heartbeat, inputData)
                            )
                );
            }

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
            keywordLoaderService.close();
            documentDbWriter.close();
            heartbeat.shutDown();
        }

        System.exit(0);
    }


}
