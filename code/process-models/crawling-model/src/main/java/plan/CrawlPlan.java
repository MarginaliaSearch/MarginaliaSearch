package plan;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.spec.CrawlerSpecificationLoader;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.Optional;

@AllArgsConstructor @NoArgsConstructor @ToString
public class CrawlPlan {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public String jobSpec;
    public WorkDir crawl;
    public WorkDir process;

    private static String rootDirRewrite = System.getProperty("crawl.rootDirRewrite");

    public Path getJobSpec() {
        return Path.of(rewrite(jobSpec));
    }

    @AllArgsConstructor @NoArgsConstructor @ToString
    public static class WorkDir {
        public String dir;
        public String logName;

        public Path getDir() {
            return Path.of(rewrite(dir));
        }
        public Path getLogFile() {
            return Path.of(rewrite(dir)).resolve(logName);
        }
    }

    private static String rewrite(String dir) {
        if (rootDirRewrite == null) {
            return dir;
        }
        String[] parts = rootDirRewrite.split(":");

        return dir.replaceFirst(parts[0], parts[1]);
    }

    public Path getCrawledFilePath(String fileName) {
        String sp1 = fileName.substring(0, 2);
        String sp2 = fileName.substring(2, 4);
        return crawl.getDir().resolve(sp1).resolve(sp2).resolve(fileName);
    }

    public Path getProcessedFilePath(String fileName) {
        String sp1 = fileName.substring(0, 2);
        String sp2 = fileName.substring(2, 4);
        return process.getDir().resolve(sp1).resolve(sp2).resolve(fileName);
    }

    public WorkLog createCrawlWorkLog() throws IOException {
        return new WorkLog(crawl.getLogFile());
    }

    public WorkLog createProcessWorkLog() throws IOException {
        return new WorkLog(process.getLogFile());
    }

    public void forEachCrawlingSpecification(Consumer<CrawlingSpecification> consumer) {
        CrawlerSpecificationLoader.readInputSpec(getJobSpec(), consumer);
    }

    public void forEachCrawlingLogEntry(Consumer<WorkLogEntry> consumer) throws FileNotFoundException {
        WorkLog.readLog(this.crawl.getLogFile(), consumer);
    }
    public void forEachProcessingLogEntry(Consumer<WorkLogEntry> consumer) throws FileNotFoundException {
        WorkLog.readLog(this.process.getLogFile(), consumer);
    }

    public void forEachCrawledDomain(Consumer<CrawledDomain> consumer) {
        final CrawledDomainReader reader = new CrawledDomainReader();

        try (Stream<WorkLogEntry> entryStream = WorkLog.streamLog(crawl.getLogFile())) {
            entryStream
                    .map(WorkLogEntry::path)
                    .map(this::getCrawledFilePath)
                    .map(reader::readOptionally)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(consumer);
        }
        catch (IOException ex) {
            logger.warn("Failed to read domains", ex);

            throw new RuntimeException(ex);
        }
    }

    public int countCrawledDomains() {
        try (Stream<WorkLogEntry> entryStream = WorkLog.streamLog(crawl.getLogFile())) {
            return (int) entryStream
                    .map(WorkLogEntry::path)
                    .count();
        }
        catch (IOException ex) {
            return 0;
        }
    }

    public void forEachCrawledDomain(Predicate<String> idReadPredicate, Consumer<CrawledDomain> consumer) {
        final CrawledDomainReader reader = new CrawledDomainReader();

        try (Stream<WorkLogEntry> entryStream = WorkLog.streamLog(crawl.getLogFile())) {
            entryStream
                    .filter(entry -> idReadPredicate.test(entry.id()))
                    .map(WorkLogEntry::path)
                    .map(this::getCrawledFilePath)
                    .filter(path -> {
                        if (!Files.exists(path)) {
                            logger.warn("File not found: {}", path);
                            return false;
                        }
                        return true;
                    })
                    .map(reader::readOptionally)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(consumer);
        }
        catch (IOException ex) {
            logger.error("Failed to read domains", ex);

            throw new RuntimeException(ex);
        }
    }
    public DomainsIterable domainsIterable() throws IOException {
        return new DomainsIterable();
    }

    public class DomainsIterable implements Iterable<CrawledDomain>, AutoCloseable {
        private final Stream<CrawledDomain> stream;

        DomainsIterable() throws IOException {
            final CrawledDomainReader reader = new CrawledDomainReader();

            stream = WorkLog.streamLog(crawl.getLogFile())
                    .map(WorkLogEntry::path)
                    .map(CrawlPlan.this::getCrawledFilePath)
                    .map(reader::readOptionally)
                    .filter(Optional::isPresent)
                    .map(Optional::get);
        }

        @Override
        public void close() {
            stream.close();
        }

        @NotNull
        @Override
        public Iterator<CrawledDomain> iterator() {
            return stream.iterator();
        }
    }
}
