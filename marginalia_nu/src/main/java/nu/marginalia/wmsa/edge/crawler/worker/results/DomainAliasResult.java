package nu.marginalia.wmsa.edge.crawler.worker.results;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.edge.crawler.worker.UploaderWorker;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@AllArgsConstructor  @ToString
public class DomainAliasResult implements WorkerResults {
    private final EdgeDomain source;
    private final EdgeDomain dest;
    private final EdgeUrl[] urls;

    @Override
    public void upload(UploaderWorker uploader) {
        uploader.onDomainAlias(source, dest, urls);
    }
}
