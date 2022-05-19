package nu.marginalia.wmsa.edge.crawler.worker.results;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.edge.crawler.domain.DomainCrawlResults;
import nu.marginalia.wmsa.edge.crawler.worker.UploaderWorker;

@AllArgsConstructor  @ToString
public class DomainCrawlerWorkerResults implements WorkerResults {
    private final DomainCrawlResults results;

    @Override
    public void upload(UploaderWorker uploader) {
        uploader.onDomainCrawlResults(results);
    }
}
