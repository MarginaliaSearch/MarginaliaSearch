package nu.marginalia.wmsa.edge.crawler.worker;

import com.google.inject.Inject;
import nu.marginalia.wmsa.edge.crawler.domain.DomainCrawlerFactory;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpRedirectResolver;
import nu.marginalia.wmsa.edge.crawler.worker.facade.TaskProvider;
import nu.marginalia.wmsa.edge.crawler.worker.facade.UploadFacade;
import nu.marginalia.wmsa.edge.crawler.worker.results.WorkerResults;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class WorkerFactory {

    private final DomainCrawlerFactory domainCrawlerFactory;
    private final TaskProvider taskProvider;
    private final HttpRedirectResolver redirectResolver;
    private final UploadFacade uploadFacade;
    private final IpBlockList blockList;

    @Inject
    public WorkerFactory(DomainCrawlerFactory domainCrawlerFactory,
                         TaskProvider taskProvider,

                         HttpRedirectResolver redirectResolver,
                         UploadFacade uploadFacade, IpBlockList blockList)
    {
        this.domainCrawlerFactory = domainCrawlerFactory;
        this.taskProvider = taskProvider;
        this.uploadFacade = uploadFacade;
        this.redirectResolver = redirectResolver;
        this.blockList = blockList;
    }

    public CrawlerIndexWorker buildIndexWorker(LinkedBlockingQueue<WorkerResults> queue, int pass) {
        return new CrawlerIndexWorker(domainCrawlerFactory, taskProvider, blockList, queue, pass);
    }


    public CrawlerDiscoverWorker buildDiscoverWorker(LinkedBlockingQueue<WorkerResults> queue) {
        return new CrawlerDiscoverWorker(domainCrawlerFactory, taskProvider, redirectResolver, blockList, queue);
    }

    public UploaderWorker buildUploader(List<LinkedBlockingQueue<WorkerResults>> queues) {
        return new UploaderWorker(queues, uploadFacade);
    }
}
