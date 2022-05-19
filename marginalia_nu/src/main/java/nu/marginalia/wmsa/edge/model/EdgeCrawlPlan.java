package nu.marginalia.wmsa.edge.model;

import lombok.*;

import java.nio.file.Path;

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

}
