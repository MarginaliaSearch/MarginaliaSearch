package plan;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.process.log.WorkLog;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.Optional;

@AllArgsConstructor @NoArgsConstructor @ToString
public class CrawlPlan {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public String jobSpec;
    public WorkDir crawl;
    public WorkDir process;

    private final static String rootDirRewrite = System.getProperty("crawl.rootDirRewrite");

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
        int sp = fileName.lastIndexOf('/');

        // Normalize the filename
        if (sp >= 0 && sp + 1< fileName.length())
            fileName = fileName.substring(sp + 1);
        if (fileName.length() < 4)
            fileName = Strings.repeat("0", 4 - fileName.length()) + fileName;

        String sp1 = fileName.substring(0, 2);
        String sp2 = fileName.substring(2, 4);
        return crawl.getDir().resolve(sp1).resolve(sp2).resolve(fileName);
    }

    public int countCrawledDomains() {
        int count = 0;
        for (var ignored : WorkLog.iterable(crawl.getLogFile())) {
            count++;
        }
        return count;
    }

    @Deprecated
    public Iterable<CrawledDomain> domainsIterable() {
        // This is no longer supported
        throw new UnsupportedOperationException();
    }

    public Iterable<SerializableCrawlDataStream> crawlDataIterable(Predicate<String> idPredicate) {
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
                        return Optional.of(CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.COMPATIBLE, path));
                    }
                    catch (IOException ex) {
                        return Optional.empty();
                    }
                });
    }
}
