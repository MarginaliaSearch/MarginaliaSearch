package nu.marginalia.converting;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.ProcessConfigurationModule;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloadSourceFactory;
import nu.marginalia.converting.writer.ConverterBatchWriter;
import nu.marginalia.converting.writer.ConverterWriter;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.util.SimpleBlockingThreadPool;
import nu.marginalia.worklog.BatchingWorkLog;
import nu.marginalia.worklog.BatchingWorkLogImpl;
import plan.CrawlPlan;
import nu.marginalia.converting.processor.DomainProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static nu.marginalia.mqapi.ProcessInboxNames.CONVERTER_INBOX;

public class ConverterMain {
    private static final Logger logger = LoggerFactory.getLogger(ConverterMain.class);
    private final DomainProcessor processor;
    private final Gson gson;
    private final ProcessHeartbeat heartbeat;
    private final MessageQueueFactory messageQueueFactory;
    private final FileStorageService fileStorageService;
    private final SideloadSourceFactory sideloadSourceFactory;

    private final int node;

    public static void main(String... args) throws Exception {

        try {
            Injector injector = Guice.createInjector(
                    new ConverterModule(),
                    new ProcessConfigurationModule("converter"),
                    new DatabaseModule()
            );

            var converter = injector.getInstance(ConverterMain.class);

            logger.info("Starting pipe");

            converter
                    .fetchInstructions()
                    .execute(converter);

            logger.info("Finished");
        }
        catch (Exception ex) {
            logger.error("Uncaught Exception", ex);
        }

        System.exit(0);
    }

    @Inject
    public ConverterMain(
            DomainProcessor processor,
            Gson gson,
            ProcessHeartbeatImpl heartbeat,
            MessageQueueFactory messageQueueFactory,
            FileStorageService fileStorageService,
            SideloadSourceFactory sideloadSourceFactory,
            ProcessConfiguration processConfiguration
            )
    {
        this.processor = processor;
        this.gson = gson;
        this.heartbeat = heartbeat;
        this.messageQueueFactory = messageQueueFactory;
        this.fileStorageService = fileStorageService;
        this.sideloadSourceFactory = sideloadSourceFactory;
        this.node = processConfiguration.node();

        heartbeat.start();
    }

    public void convert(Collection<? extends SideloadSource> sideloadSources, Path writeDir) throws Exception {
        try (var writer = new ConverterBatchWriter(writeDir, 0);
             var taskHeartbeat = heartbeat.createAdHocTaskHeartbeat("Sideloading");
             BatchingWorkLog batchingWorkLog = new BatchingWorkLogImpl(writeDir.resolve("processor.log"))
        ) {

            int i = 0;
            for (var sideloadSource : sideloadSources) {
                logger.info("Sideloading {}", sideloadSource.domainName());

                taskHeartbeat.progress(sideloadSource.domainName(), i++, sideloadSources.size());

                writer.write(sideloadSource);
            }
            taskHeartbeat.progress("Finished", i, sideloadSources.size());

            // We write an empty log with just a finish marker for the sideloading action
            batchingWorkLog.logFinishedBatch();
        }
    }

    public void convert(CrawlPlan plan) throws Exception {

        final int maxPoolSize = Math.clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 32);

        try (BatchingWorkLog batchingWorkLog = new BatchingWorkLogImpl(plan.process.getLogFile());
             ConverterWriter converterWriter = new ConverterWriter(batchingWorkLog, plan.process.getDir()))
        {
            var pool = new SimpleBlockingThreadPool("ConverterThread", maxPoolSize, 2);

            int totalDomains = plan.countCrawledDomains();
            AtomicInteger processedDomains = new AtomicInteger(0);
            logger.info("Processing {} domains", totalDomains);

            // Advance the progress bar to the current position if this is a resumption
            processedDomains.set(batchingWorkLog.size());
            heartbeat.setProgress(processedDomains.get() / (double) totalDomains);

            for (var domain : plan.crawlDataIterable(id -> !batchingWorkLog.isItemProcessed(id)))
            {
                pool.submit(() -> {
                    ProcessedDomain processed = processor.process(domain);
                    converterWriter.accept(processed);

                    heartbeat.setProgress(processedDomains.incrementAndGet() / (double) totalDomains);
                });
            }

            // Grace period in case we're loading like 1 item
            Thread.sleep(100);

            pool.shutDown();
            do {
                System.out.println("Waiting for pool to terminate... " + pool.getActiveCount() + " remaining");
            } while (!pool.awaitTermination(60, TimeUnit.SECONDS));
        }
    }

    private abstract static class ConvertRequest {
        private final MqMessage message;
        private final MqSingleShotInbox inbox;

        private ConvertRequest(MqMessage message, MqSingleShotInbox inbox) {
            this.message = message;
            this.inbox = inbox;
        }

        public abstract void execute(ConverterMain converterMain) throws Exception;

        public void ok() {
            inbox.sendResponse(message, MqInboxResponse.ok());
        }
        public void err() {
            inbox.sendResponse(message, MqInboxResponse.err());
        }
    }

    private static class SideloadAction extends ConvertRequest {

        private final Collection<? extends SideloadSource> sideloadSources;
        private final Path workDir;

        SideloadAction(SideloadSource sideloadSource,
                       Path workDir,
                       MqMessage message, MqSingleShotInbox inbox) {
            super(message, inbox);
            this.sideloadSources = List.of(sideloadSource);
            this.workDir = workDir;
        }
        SideloadAction(Collection<? extends SideloadSource> sideloadSources,
                       Path workDir,
                       MqMessage message, MqSingleShotInbox inbox) {
            super(message, inbox);
            this.sideloadSources = sideloadSources;
            this.workDir = workDir;
        }
        @Override
        public void execute(ConverterMain converterMain) throws Exception {
            try {
                converterMain.convert(sideloadSources, workDir);
                ok();
            }
            catch (Exception ex) {
                logger.error("Error sideloading", ex);
                err();
            }
        }
    }

    private static class ConvertCrawlDataAction extends ConvertRequest {
        private final CrawlPlan plan;

        private ConvertCrawlDataAction(CrawlPlan plan, MqMessage message, MqSingleShotInbox inbox) {
            super(message, inbox);
            this.plan = plan;
        }

        @Override
        public void execute(ConverterMain converterMain) throws Exception {
            try {
                converterMain.convert(plan);
                ok();
            }
            catch (Exception ex) {
                err();
            }
        }
    }


    private ConvertRequest fetchInstructions() throws Exception {

        var inbox = messageQueueFactory.createSingleShotInbox(CONVERTER_INBOX, node, UUID.randomUUID());

        var msgOpt = getMessage(inbox, nu.marginalia.mqapi.converting.ConvertRequest.class.getSimpleName());
        var msg = msgOpt.orElseThrow(() -> new RuntimeException("No message received"));

        var request = gson.fromJson(msg.payload(), nu.marginalia.mqapi.converting.ConvertRequest.class);

        return switch(request.action) {
            case ConvertCrawlData -> {
                var crawlData = fileStorageService.getStorage(request.crawlStorage);
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                var plan = new CrawlPlan(null,
                        new CrawlPlan.WorkDir(crawlData.path(), "crawler.log"),
                        new CrawlPlan.WorkDir(processData.path(), "processor.log"));

                yield new ConvertCrawlDataAction(plan, msg, inbox);
            }
            case SideloadEncyclopedia -> {
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                yield new SideloadAction(sideloadSourceFactory.sideloadEncyclopediaMarginaliaNu(Path.of(request.inputSource), request.baseUrl),
                        processData.asPath(),
                        msg, inbox);
            }
            case SideloadDirtree -> {
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                yield new SideloadAction(
                        sideloadSourceFactory.sideloadDirtree(Path.of(request.inputSource)),
                        processData.asPath(),
                        msg, inbox);
            }
            case SideloadStackexchange -> {
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                yield new SideloadAction(sideloadSourceFactory.sideloadStackexchange(Path.of(request.inputSource)),
                        processData.asPath(),
                        msg, inbox);
            }
        };
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
