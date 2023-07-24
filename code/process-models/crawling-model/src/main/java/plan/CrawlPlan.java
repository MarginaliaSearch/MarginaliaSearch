package plan;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.crawling.model.spec.CrawlerSpecificationLoader;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.process.log.WorkLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Predicate;
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

    public Iterable<CrawlingSpecification> crawlingSpecificationIterable() {
        return CrawlerSpecificationLoader.asIterable(getJobSpec());
    }

    public int countCrawledDomains() {
        int count = 0;
        for (var ignored : WorkLog.iterable(crawl.getLogFile())) {
            count++;
        }
        return count;
    }

    public Iterable<CrawledDomain> domainsIterable() {
        final CrawledDomainReader reader = new CrawledDomainReader();

        return WorkLog.iterableMap(crawl.getLogFile(),
                entry -> {
                    var path = getCrawledFilePath(entry.path());
                    if (!Files.exists(path)) {
                        logger.warn("File not found: {}", path);
                        return Optional.empty();
                    }
                    return reader.readOptionally(path);
                });
    }

    public Iterable<CrawledDomain> domainsIterable(Predicate<String> idPredicate) {
        final CrawledDomainReader reader = new CrawledDomainReader();

        return WorkLog.iterableMap(crawl.getLogFile(),
                entry -> {
                    if (!idPredicate.test(entry.id())) {
                        return Optional.empty();
                    }

                    var path = getCrawledFilePath(entry.path());

                    if (!Files.exists(path)) {
                        logger.warn("File not found: {}", path);
                        return Optional.empty();
                    }
                    return reader.readOptionally(path);
                });
    }


    public Iterable<Iterator<SerializableCrawlData>> crawlDataIterable(Predicate<String> idPredicate) {
        final CrawledDomainReader reader = new CrawledDomainReader();

        return WorkLog.iterableMap(crawl.getLogFile(),
                entry -> {
                    if (!idPredicate.test(entry.id())) {
                        return Optional.empty();
                    }

                    var path = getCrawledFilePath(entry.path());

                    if (!Files.exists(path)) {
                        logger.warn("File not found: {}", path);
                        return Optional.empty();
                    }

                    try {
                        return Optional.of(reader.createIterator(path));
                    }
                    catch (IOException ex) {
                        return Optional.empty();
                    }
                });
    }
}
