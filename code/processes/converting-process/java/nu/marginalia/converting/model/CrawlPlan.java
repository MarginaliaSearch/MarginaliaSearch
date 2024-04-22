package nu.marginalia.converting.model;

import nu.marginalia.process.log.WorkLog;

public record CrawlPlan(String jobSpec, WorkDir crawl, WorkDir process) {

    public int countCrawledDomains() {
        int count = 0;
        for (var ignored : WorkLog.iterable(crawl.getLogFile())) {
            count++;
        }
        return count;
    }

}
