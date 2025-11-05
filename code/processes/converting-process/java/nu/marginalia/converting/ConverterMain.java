package nu.marginalia.converting;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.converting.model.CrawlPlan;
import nu.marginalia.converting.model.WorkDir;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloadSourceFactory;
import nu.marginalia.converting.writer.ConverterBatchWritableIf;
import nu.marginalia.converting.writer.ConverterBatchWriter;
import nu.marginalia.converting.writer.ConverterWriter;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.converting.ConvertRequest;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.util.SimpleBlockingThreadPool;
import nu.marginalia.worklog.BatchingWorkLog;
import nu.marginalia.worklog.BatchingWorkLogImpl;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static nu.marginalia.mqapi.ProcessInboxNames.CONVERTER_INBOX;

public class ConverterMain extends ProcessMainClass {
    private static final Logger logger = LoggerFactory.getLogger(ConverterMain.class);
    private final DomainProcessor processor;
    private final ProcessHeartbeat heartbeat;
    private final FileStorageService fileStorageService;
    private final SideloadSourceFactory sideloadSourceFactory;
    private static final int SIDELOAD_THRESHOLD = Integer.getInteger("converter.sideloadThreshold", 10_000);

    public static void main(String... args) throws Exception {

        try {
            Injector injector = Guice.createInjector(
                    new ConverterModule(),
                    new ProcessConfigurationModule("converter"),
                    new ServiceDiscoveryModule(),
                    new DatabaseModule(false)
            );

            var converter = injector.getInstance(ConverterMain.class);

            logger.info("Starting pipe");

            Instructions<ConvertRequest> instructions = converter.fetchInstructions(ConvertRequest.class);

            converter.createAction(instructions)
                    .execute(converter);

            logger.info("Finished");
        }
        catch (Exception ex) {
            logger.error("Uncaught Exception", ex);
        }

        System.exit(0);
    }

    private Action createAction(Instructions<ConvertRequest> instructions) throws SQLException, IOException {
        var request = instructions.value();
        final Path inputPath = request.getInputPath();

        return switch (request.action) {
            case ConvertCrawlData -> {
                var crawlData = fileStorageService.getStorage(request.crawlStorage);
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                var plan = new CrawlPlan(null,
                        new WorkDir(crawlData.asPath().toString(), "crawler.log"),
                        new WorkDir(processData.asPath().toString(), "processor.log")
                );

                yield new ConvertCrawlDataAction(plan, instructions);
            }
            case SideloadEncyclopedia -> {
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                yield new SideloadAction(
                        sideloadSourceFactory.sideloadEncyclopediaMarginaliaNu(inputPath, request.baseUrl),
                        processData.asPath(),
                        instructions);
            }
            case SideloadDirtree -> {
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                yield new SideloadAction(
                        sideloadSourceFactory.sideloadDirtree(inputPath),
                        processData.asPath(),
                        instructions);
            }
            case SideloadWarc -> {
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                yield new SideloadAction(
                        sideloadSourceFactory.sideloadWarc(inputPath),
                        processData.asPath(),
                        instructions);
            }
            case SideloadReddit -> {
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                yield new SideloadAction(
                        sideloadSourceFactory.sideloadReddit(inputPath),
                        processData.asPath(),
                        instructions);
            }
            case SideloadStackexchange -> {
                var processData = fileStorageService.getStorage(request.processedDataStorage);

                yield new SideloadAction(
                        sideloadSourceFactory.sideloadStackexchange(inputPath),
                        processData.asPath(),
                        instructions);
            }
        };
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
        super(messageQueueFactory, processConfiguration, gson, CONVERTER_INBOX);

        this.processor = processor;
        this.heartbeat = heartbeat;
        this.fileStorageService = fileStorageService;
        this.sideloadSourceFactory = sideloadSourceFactory;

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

                writer.writeSideloadSource(sideloadSource);
            }
            taskHeartbeat.progress("Finished", i, sideloadSources.size());

            // We write an empty log with just a finish marker for the sideloading action
            batchingWorkLog.logFinishedBatch();
        }
    }

    public void convert(int totalDomains, WorkDir crawlDir, WorkDir processedDir) throws Exception {


        final int defaultPoolSize = Boolean.getBoolean("system.conserveMemory")
                ? Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4)   // <-- conserve memory
                : Math.clamp(Runtime.getRuntime().availableProcessors() - 5, 1, 32); // <-- a more liberal pool size

        final int maxPoolSize = Integer.getInteger("converter.poolSize", defaultPoolSize);

        try (BatchingWorkLog batchingWorkLog = new BatchingWorkLogImpl(processedDir.getLogFile());
             ConverterWriter converterWriter = new ConverterWriter(batchingWorkLog, processedDir.getDir()))
        {
            var pool = new SimpleBlockingThreadPool("ConverterThread", maxPoolSize, 2);

            AtomicInteger processedDomains = new AtomicInteger(0);
            logger.info("Processing {} domains", totalDomains);

            // Advance the progress bar to the current position if this is a resumption
            processedDomains.set(batchingWorkLog.size());
            heartbeat.setProgress(processedDomains.get() / (double) totalDomains);

            logger.info("Processing small items");

            // We separate the large and small domains to reduce the number of critical sections,
            // as the large domains have a separate processing track that doesn't store everything
            // in memory

            final List<Path> bigTasks = new ArrayList<>();

            // First process the small items
            for (var dataPath : WorkLog.iterableMap(crawlDir.getLogFile(),
                    new CrawlDataLocator(crawlDir.getDir(), batchingWorkLog)))
            {
                if (SerializableCrawlDataStream.getSizeHint(dataPath) >= SIDELOAD_THRESHOLD) {
                    bigTasks.add(dataPath);
                    continue;
                }

                pool.submit(() -> {
                    try (var dataStream = SerializableCrawlDataStream.openDataStream(dataPath)) {
                        ConverterBatchWritableIf writable = processor.fullProcessing(dataStream) ;
                        converterWriter.accept(writable);
                    }
                    catch (Exception ex) {
                        logger.info("Error in processing", ex);
                    }
                    finally {
                        heartbeat.setProgress(processedDomains.incrementAndGet() / (double) totalDomains);
                    }
                });
            }

            // Grace period in case we're loading like 1 item
            Thread.sleep(100);

            pool.shutDown();
            do {
                System.out.println("Waiting for pool to terminate... " + pool.getActiveCount() + " remaining");
            } while (!pool.awaitTermination(60, TimeUnit.SECONDS));

            logger.info("Processing large items");

            try (var hb = heartbeat.createAdHocTaskHeartbeat("Large Domains")) {
                int bigTaskIdx = 0;
                // Next the big items domain-by-domain
                for (var dataPath : bigTasks) {
                    hb.progress(dataPath.toFile().getName(), bigTaskIdx++, bigTasks.size());

                    try {
                        // SerializableCrawlDataStream is autocloseable, we can't try-with-resources because then it will be
                        // closed before it's consumed by the converterWriter.  Instead, the converterWriter guarantees it
                        // will close it after it's consumed.

                        var stream = SerializableCrawlDataStream.openDataStream(dataPath);
                        ConverterBatchWritableIf writable = processor.simpleProcessing(stream, SerializableCrawlDataStream.getSizeHint(dataPath));

                        converterWriter.accept(writable);
                    }
                    catch (Exception ex) {
                        logger.info("Error in processing", ex);
                    }
                    finally {
                        heartbeat.setProgress(processedDomains.incrementAndGet() / (double) totalDomains);
                    }
                }
            }

            logger.info("Processing complete");
        }
    }

    private static class CrawlDataLocator implements Function<WorkLogEntry, Optional<Path>> {

        private final Path crawlRootDir;
        private final BatchingWorkLog batchingWorkLog;

        CrawlDataLocator(Path crawlRootDir, BatchingWorkLog workLog) {
            this.crawlRootDir = crawlRootDir;
            this.batchingWorkLog = workLog;
        }

        @Override
        public Optional<Path> apply(WorkLogEntry entry) {
            if (batchingWorkLog.isItemProcessed(entry.id())) {
                return Optional.empty();
            }

            var path = getCrawledFilePath(crawlRootDir, entry.path());

            if (!Files.exists(path)) {
                logger.warn("File not found: {}", path);
                return Optional.empty();
            }

            try {
                return Optional.of(path);
            }
            catch (Exception ex) {
                return Optional.empty();
            }
        }

        private Path getCrawledFilePath(Path crawlDir, String fileName) {
            int sp = fileName.lastIndexOf('/');

            // Normalize the filename
            if (sp >= 0 && sp + 1< fileName.length())
                fileName = fileName.substring(sp + 1);
            if (fileName.length() < 4)
                fileName = Strings.repeat("0", 4 - fileName.length()) + fileName;

            String sp1 = fileName.substring(0, 2);
            String sp2 = fileName.substring(2, 4);
            return crawlDir.resolve(sp1).resolve(sp2).resolve(fileName);
        }
    }

    private abstract static class Action {
        final Instructions<?> instructions;

        public Action(Instructions<?> instructions) {
            this.instructions = instructions;
        }

        public abstract void execute(ConverterMain converterMain) throws Exception;

        public void ok() {
            instructions.ok();
        }
        public void err() {
            instructions.err();
        }
    }

    private static class SideloadAction extends Action {

        private final Collection<? extends SideloadSource> sideloadSources;
        private final Path workDir;

        SideloadAction(SideloadSource sideloadSource,
                       Path workDir,
                       Instructions<?> instructions) {
            super(instructions);
            this.sideloadSources = List.of(sideloadSource);
            this.workDir = workDir;
        }

        SideloadAction(Collection<? extends SideloadSource> sideloadSources,
                       Path workDir,
                       Instructions<?> instructions) {
            super(instructions);
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

    private static class ConvertCrawlDataAction extends Action {
        private final CrawlPlan plan;

        private ConvertCrawlDataAction(CrawlPlan plan,
                                       Instructions<?> instructions) {
            super(instructions);
            this.plan = plan;
        }

        @Override
        public void execute(ConverterMain converterMain) throws Exception {
            try {
                converterMain.convert(plan.countCrawledDomains(), plan.crawl(), plan.process());
                ok();
            }
            catch (Exception ex) {
                logger.error("Error converting", ex);

                err();
            }
        }
    }

}
