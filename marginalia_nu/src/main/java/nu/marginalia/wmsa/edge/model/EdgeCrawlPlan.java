package nu.marginalia.wmsa.edge.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.edge.crawling.CrawledDomainReader;
import nu.marginalia.wmsa.edge.crawling.WorkLog;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

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

     public void forEachCrawledDomain(Consumer<CrawledDomain> consumer) {
        final CrawledDomainReader reader = new CrawledDomainReader();

        WorkLog.readLog(crawl.getLogFile(), entry -> {
            try {
                consumer.accept(reader.read(getCrawledFilePath(entry.path())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
