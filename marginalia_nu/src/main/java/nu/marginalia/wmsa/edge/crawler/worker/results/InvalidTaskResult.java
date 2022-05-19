package nu.marginalia.wmsa.edge.crawler.worker.results;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.edge.crawler.worker.UploaderWorker;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

@AllArgsConstructor @ToString
public class InvalidTaskResult implements WorkerResults {
    private final EdgeDomain domain;
    public String why;


    @Override
    public void upload(UploaderWorker uploader) {
        uploader.onInvalidDomain(domain, why);
    }
}
