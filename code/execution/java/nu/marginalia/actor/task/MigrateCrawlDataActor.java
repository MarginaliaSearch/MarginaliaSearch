package nu.marginalia.actor.task;

import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.io.CrawlerOutputFile;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.slop.SlopCrawlDataRecord;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public class MigrateCrawlDataActor extends RecordActorPrototype {

    private final FileStorageService fileStorageService;
    private final ServiceHeartbeat serviceHeartbeat;
    private static final Logger logger = LoggerFactory.getLogger(MigrateCrawlDataActor.class);

    @Inject
    public MigrateCrawlDataActor(Gson gson, FileStorageService fileStorageService, ServiceHeartbeat serviceHeartbeat) {
        super(gson);

        this.fileStorageService = fileStorageService;
        this.serviceHeartbeat = serviceHeartbeat;
    }

    public record Run(long fileStorageId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Run(long fileStorageId) -> {

                FileStorage storage = fileStorageService.getStorage(FileStorageId.of(fileStorageId));
                Path root = storage.asPath();

                Path crawlerLog = root.resolve("crawler.log");
                Path newCrawlerLog = Files.createTempFile(root, "crawler", ".migrate.log");

                int totalEntries = WorkLog.countEntries(crawlerLog);

                try (WorkLog workLog = new WorkLog(newCrawlerLog);
                     var heartbeat = serviceHeartbeat.createServiceAdHocTaskHeartbeat("Migrating")
                ) {
                    int entryIdx = 0;

                    for (Map.Entry<WorkLogEntry, Path> item : WorkLog.iterableMap(crawlerLog, new CrawlDataLocator(root))) {

                        final WorkLogEntry entry = item.getKey();
                        final Path inputPath = item.getValue();

                        Path outputPath = inputPath;
                        heartbeat.progress("Migrating" + inputPath.getFileName(), entryIdx++, totalEntries);

                        if (inputPath.toString().endsWith(".parquet")) {
                            String domain = entry.id();
                            String id = Integer.toHexString(domain.hashCode());

                            outputPath = CrawlerOutputFile.createSlopPath(root, id, domain);

                            if (Files.exists(inputPath)) {
                                try {
                                    SlopCrawlDataRecord.convertFromParquet(inputPath, outputPath);
                                    Files.deleteIfExists(inputPath);
                                } catch (Exception ex) {
                                    outputPath = inputPath; // don't update the work log on error
                                    logger.error("Failed to convert " + inputPath, ex);
                                }
                            }
                            else if (!Files.exists(inputPath) && !Files.exists(outputPath)) {
                                // if the input file is missing, and the output file is missing, we just write the log
                                // record identical to the old one
                                outputPath = inputPath;
                            }
                        }

                        // Write a log entry for the (possibly) converted file
                        workLog.setJobToFinished(entry.id(), outputPath.toString(), entry.cnt());
                    }
                }

                Path oldCrawlerLog = Files.createTempFile(root, "crawler-", ".migrate.old.log");
                Files.move(crawlerLog, oldCrawlerLog, StandardCopyOption.REPLACE_EXISTING);
                Files.move(newCrawlerLog, crawlerLog);

                yield new End();
            }
            default -> new Error();
        };
    }

    private static class CrawlDataLocator implements Function<WorkLogEntry, Optional<Map.Entry<WorkLogEntry, Path>>> {

        private final Path crawlRootDir;

        CrawlDataLocator(Path crawlRootDir) {
            this.crawlRootDir = crawlRootDir;
        }

        @Override
        public Optional<Map.Entry<WorkLogEntry, Path>> apply(WorkLogEntry entry) {
            var path = getCrawledFilePath(crawlRootDir, entry.path());

            if (!Files.exists(path)) {
                return Optional.empty();
            }

            try {
                return Optional.of(Map.entry(entry, path));
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

    @Override
    public String describe() {
        return "Migrates crawl data to the latest format";
    }
}
