package nu.marginalia.converting;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloadSourceFactory;
import nu.marginalia.converting.writer.ConverterWriter;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.mqapi.converting.ConvertAction;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.worklog.BatchingWorkLog;
import nu.marginalia.worklog.BatchingWorkLogImpl;
import org.checkerframework.checker.units.qual.C;
import plan.CrawlPlan;
import nu.marginalia.converting.compiler.InstructionsCompiler;
import nu.marginalia.converting.processor.DomainProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static nu.marginalia.mqapi.ProcessInboxNames.CONVERTER_INBOX;

public class ConverterMain {
    private static final Logger logger = LoggerFactory.getLogger(ConverterMain.class);
    private final DomainProcessor processor;
    private final InstructionsCompiler compiler;
    private final Gson gson;
    private final ProcessHeartbeat heartbeat;
    private final MessageQueueFactory messageQueueFactory;
    private final FileStorageService fileStorageService;
    private final SideloadSourceFactory sideloadSourceFactory;

    public static void main(String... args) throws Exception {
        Injector injector = Guice.createInjector(
                new ConverterModule(),
                new DatabaseModule()
        );

        var converter = injector.getInstance(ConverterMain.class);

        logger.info("Starting pipe");

        converter
                .fetchInstructions()
                .execute(converter);

        logger.info("Finished");

        System.exit(0);
    }

    @Inject
    public ConverterMain(
            DomainProcessor processor,
            InstructionsCompiler compiler,
            Gson gson,
            ProcessHeartbeatImpl heartbeat,
            MessageQueueFactory messageQueueFactory,
            FileStorageService fileStorageService,
            SideloadSourceFactory sideloadSourceFactory
            )
    {
        this.processor = processor;
        this.compiler = compiler;
        this.gson = gson;
        this.heartbeat = heartbeat;
        this.messageQueueFactory = messageQueueFactory;
        this.fileStorageService = fileStorageService;
        this.sideloadSourceFactory = sideloadSourceFactory;

        heartbeat.start();
    }

    public void convert(SideloadSource sideloadSource, Path writeDir) throws Exception {
        int maxPoolSize = 16;

        try (WorkLog workLog = new WorkLog(writeDir.resolve("processor.log"));
             ConversionLog conversionLog = new ConversionLog(writeDir)) {
            var instructionWriter = new InstructionWriterFactory(conversionLog, writeDir, gson);

            final String where;
            final int size;

            try (var writer = instructionWriter.createInstructionsForDomainWriter(sideloadSource.getId())) {
                compiler.compileStreaming(sideloadSource, writer::accept);
                where = writer.getFileName();
                size = writer.getSize();
            }

            workLog.setJobToFinished(sideloadSource.getId(), where, size);
        }
    }

    public void convert(CrawlPlan plan) throws Exception {

        final int maxPoolSize = Runtime.getRuntime().availableProcessors();



        try (BatchingWorkLog batchingWorkLog = new BatchingWorkLogImpl(plan.process.getLogFile());
             ConverterWriter converterWriter = new ConverterWriter(batchingWorkLog, plan.process.getDir());
             ConversionLog log = new ConversionLog(plan.process.getDir())) {
            var instructionWriter = new InstructionWriterFactory(log, plan.process.getDir(), gson);

            var pool = new DumbThreadPool(maxPoolSize, 2);

            int totalDomains = plan.countCrawledDomains();
            AtomicInteger processedDomains = new AtomicInteger(0);

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

        private final SideloadSource sideloadSource;
        private final Path workDir;

        SideloadAction(SideloadSource sideloadSource,
                       Path workDir,
                       MqMessage message, MqSingleShotInbox inbox) {
            super(message, inbox);
            this.sideloadSource = sideloadSource;
            this.workDir = workDir;
        }

        @Override
        public void execute(ConverterMain converterMain) throws Exception {
            try {
                converterMain.convert(sideloadSource, workDir);
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

        var inbox = messageQueueFactory.createSingleShotInbox(CONVERTER_INBOX, UUID.randomUUID());

        var msgOpt = getMessage(inbox, nu.marginalia.mqapi.converting.ConvertRequest.class.getSimpleName());
        var msg = msgOpt.orElseThrow(() -> new RuntimeException("No message received"));

        var request = gson.fromJson(msg.payload(), nu.marginalia.mqapi.converting.ConvertRequest.class);

        if (request.action == ConvertAction.ConvertCrawlData) {

            var crawlData = fileStorageService.getStorage(request.crawlStorage);
            var processData = fileStorageService.getStorage(request.processedDataStorage);

            var plan = new CrawlPlan(null,
                    new CrawlPlan.WorkDir(crawlData.path(), "crawler.log"),
                    new CrawlPlan.WorkDir(processData.path(), "processor.log"));

            return new ConvertCrawlDataAction(plan, msg, inbox);
        }

        if (request.action == ConvertAction.SideloadEncyclopedia) {
            var processData = fileStorageService.getStorage(request.processedDataStorage);
            var filePath = Path.of(request.inputSource);

            return new SideloadAction(sideloadSourceFactory.sideloadEncyclopediaMarginaliaNu(filePath),
                    processData.asPath(),
                    msg, inbox);
        }

        if (request.action == ConvertAction.SideloadStackexchange) {
            var processData = fileStorageService.getStorage(request.processedDataStorage);
            var filePath = Path.of(request.inputSource);
            var domainName = filePath.toFile().getName().substring(0, filePath.toFile().getName().lastIndexOf('.'));
            return new SideloadAction(sideloadSourceFactory.sideloadStackexchange(filePath, domainName),
                    processData.asPath(),
                    msg, inbox);
        }

        else {
            throw new RuntimeException("Unknown action: " + request.action);
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
