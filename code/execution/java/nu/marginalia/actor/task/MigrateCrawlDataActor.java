package nu.marginalia.actor.task;

import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.io.CrawlerOutputFile;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import nu.marginalia.slop.SlopCrawlDataRecord;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public class MigrateCrawlDataActor extends RecordActorPrototype {

    private final FileStorageService fileStorageService;

    private static final Logger logger = LoggerFactory.getLogger(MigrateCrawlDataActor.class);

    @Inject
    public MigrateCrawlDataActor(Gson gson, FileStorageService fileStorageService) {
        super(gson);

        this.fileStorageService = fileStorageService;
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

                try (WorkLog workLog = new WorkLog(newCrawlerLog)) {
                    for (Map.Entry<WorkLogEntry, Path> item : WorkLog.iterableMap(crawlerLog, new CrawlDataLocator(root))) {

                        var entry = item.getKey();
                        var path = item.getValue();

                        logger.info("Converting {}", entry.id());


                        if (path.toFile().getName().endsWith(".parquet")) {
                            String domain = entry.id();
                            String id = Integer.toHexString(domain.hashCode());

                            Path outputFile = CrawlerOutputFile.createSlopPath(root, id, domain);

                            SlopCrawlDataRecord.convertFromParquet(path, outputFile);

                            workLog.setJobToFinished(entry.id(), outputFile.toString(), entry.cnt());
                        }
                        else {
                            workLog.setJobToFinished(entry.id(), path.toString(), entry.cnt());
                        }
                    }
                }

                Path oldCrawlerLog = Files.createTempFile(root, "crawler-", ".migrate.old.log");
                Files.move(crawlerLog, oldCrawlerLog);
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
