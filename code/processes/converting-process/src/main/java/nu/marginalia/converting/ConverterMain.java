package nu.marginalia.converting;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.module.DatabaseModule;
import plan.CrawlPlan;
import nu.marginalia.converting.compiler.InstructionsCompiler;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.processor.DomainProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
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

    public static void main(String... args) throws Exception {


        Injector injector = Guice.createInjector(
                new ConverterModule(),
                new DatabaseModule()
        );

        var converter = injector.getInstance(ConverterMain.class);

        logger.info("Starting pipe");

        var request = converter.fetchInstructions();
        try {
            converter.convert(request);
            request.ok();
        }
        catch (Exception ex) {
            logger.error("Conversion failed", ex);
            request.err();
        }

        logger.info("Finished");

        System.exit(0);
    }

    @Inject
    public ConverterMain(
            DomainProcessor processor,
            InstructionsCompiler compiler,
            Gson gson,
            ProcessHeartbeat heartbeat,
            MessageQueueFactory messageQueueFactory,
            FileStorageService fileStorageService
            ) {
        this.processor = processor;
        this.compiler = compiler;
        this.gson = gson;
        this.heartbeat = heartbeat;
        this.messageQueueFactory = messageQueueFactory;
        this.fileStorageService = fileStorageService;

        heartbeat.start();
    }

    public void convert(ConvertRequest request) throws Exception {

        var plan = request.getPlan();

        final int maxPoolSize = 16;

        try (WorkLog processLog = plan.createProcessWorkLog();
             ConversionLog log = new ConversionLog(plan.process.getDir())) {
            var instructionWriter = new InstructionWriterFactory(log, plan.process.getDir(), gson);

            Semaphore semaphore = new Semaphore(maxPoolSize);
            var pool = new ThreadPoolExecutor(
                    maxPoolSize/4,
                    maxPoolSize,
                    5, TimeUnit.MINUTES,
                    new LinkedBlockingQueue<>(8)
            );

            int totalDomains = plan.countCrawledDomains();
            AtomicInteger processedDomains = new AtomicInteger(0);

            // Advance the progress bar to the current position if this is a resumption
            processedDomains.set(processLog.countFinishedJobs());
            heartbeat.setProgress(processedDomains.get() / (double) totalDomains);

            for (var domain : plan.crawlDataIterable(id -> !processLog.isJobFinished(id)))
            {
                semaphore.acquire();
                pool.execute(() -> {
                    try {
                        ProcessedDomain processed = processor.process(domain);

                        final String where;
                        final int size;

                        try (var writer = instructionWriter.createInstructionsForDomainWriter(processed.id)) {
                            compiler.compile(processed, writer::accept);
                            where = writer.getFileName();
                            size = writer.getSize();
                        }

                        processLog.setJobToFinished(processed.id, where, size);
                        heartbeat.setProgress(processedDomains.incrementAndGet() / (double) totalDomains);
                    }
                    catch (IOException ex) {
                        logger.warn("IO exception in converter", ex);
                    }
                    finally {
                        semaphore.release();
                    }
                });
            }

            pool.shutdown();
            do {
                System.out.println("Waiting for pool to terminate... " + pool.getActiveCount() + " remaining");
            } while (!pool.awaitTermination(60, TimeUnit.SECONDS));

            request.ok();
        }
        catch (Exception e) {
            request.err();
            throw e;
        }
    }

    private static class ConvertRequest {
        private final CrawlPlan plan;
        private final MqMessage message;
        private final MqSingleShotInbox inbox;

        ConvertRequest(CrawlPlan plan, MqMessage message, MqSingleShotInbox inbox) {
            this.plan = plan;
            this.message = message;
            this.inbox = inbox;
        }

        public CrawlPlan getPlan() {
            return plan;
        }

        public void ok() {
            inbox.sendResponse(message, MqInboxResponse.ok());
        }
        public void err() {
            inbox.sendResponse(message, MqInboxResponse.err());
        }

    }

    private ConvertRequest fetchInstructions() throws Exception {

        var inbox = messageQueueFactory.createSingleShotInbox(CONVERTER_INBOX, UUID.randomUUID());

        var msgOpt = getMessage(inbox, nu.marginalia.mqapi.converting.ConvertRequest.class.getSimpleName());
        var msg = msgOpt.orElseThrow(() -> new RuntimeException("No message received"));

        var request = gson.fromJson(msg.payload(), nu.marginalia.mqapi.converting.ConvertRequest.class);

        var crawlData = fileStorageService.getStorage(request.crawlStorage);
        var processData = fileStorageService.getStorage(request.processedDataStorage);

        var plan = new CrawlPlan(null,
                        new CrawlPlan.WorkDir(crawlData.path(), "crawler.log"),
                        new CrawlPlan.WorkDir(processData.path(), "processor.log"));

        return new ConvertRequest(plan, msg, inbox);
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
