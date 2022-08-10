package nu.marginalia.wmsa.edge.model;

import com.google.errorprone.annotations.MustBeClosed;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.edge.crawling.CrawledDomainReader;
import nu.marginalia.wmsa.edge.crawling.CrawlerSpecificationLoader;
import nu.marginalia.wmsa.edge.crawling.WorkLog;
import nu.marginalia.wmsa.edge.crawling.model.CrawlLogEntry;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.crawling.model.CrawlingSpecification;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

@AllArgsConstructor @NoArgsConstructor @ToString
public class EdgeCrawlPlan {
    public String jobSpec;
    public WorkDir crawl;
    public WorkDir process;

    public Path getJobSpec() {
        return Path.of(jobSpec);
    }

    @AllArgsConstructor @NoArgsConstructor @ToString
    public static class WorkDir {
        public String dir;
        public String logName;

        public Path getDir() {
            return Path.of(dir);
        }
        public Path getLogFile() {
            return Path.of(dir).resolve(logName);
        }
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

    public void forEachCrawlingLogEntry(Consumer<CrawlLogEntry> consumer) {
        WorkLog.readLog(this.crawl.getLogFile(), consumer);
    }
    public void forEachProcessingLogEntry(Consumer<CrawlLogEntry> consumer) {
        WorkLog.readLog(this.process.getLogFile(), consumer);
    }

    public void forEachCrawledDomain(Consumer<CrawledDomain> consumer) {
        final CrawledDomainReader reader = new CrawledDomainReader();

        try (Stream<CrawlLogEntry> entryStream = WorkLog.streamLog(crawl.getLogFile())) {
            entryStream
                    .map(CrawlLogEntry::path)
                    .map(this::getCrawledFilePath)
                    .map(reader::readRuntimeExcept)
                    .forEach(consumer);
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @MustBeClosed
    public DomainsIterable domainsIterable() throws IOException {
        return new DomainsIterable();
    }

    public class DomainsIterable implements Iterable<CrawledDomain>, AutoCloseable {
        private final Stream<CrawledDomain> stream;

        DomainsIterable() throws IOException {
            final CrawledDomainReader reader = new CrawledDomainReader();

            stream = WorkLog.streamLog(crawl.getLogFile())
                    .map(CrawlLogEntry::path)
                    .map(EdgeCrawlPlan.this::getCrawledFilePath)
                    .map(reader::readRuntimeExcept);
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
