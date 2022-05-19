package nu.marginalia.wmsa.edge.crawler.worker;

import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.wmsa.edge.crawler.domain.DomainCrawlerFactory;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpRedirectResolver;
import nu.marginalia.wmsa.edge.crawler.worker.facade.TaskProvider;
import nu.marginalia.wmsa.edge.crawler.worker.results.DomainAliasResult;
import nu.marginalia.wmsa.edge.crawler.worker.results.DomainCrawlerWorkerResults;
import nu.marginalia.wmsa.edge.crawler.worker.results.InvalidTaskResult;
import nu.marginalia.wmsa.edge.crawler.worker.results.WorkerResults;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

public class CrawlerDiscoverWorker implements Worker {

    private final HttpRedirectResolver redirectResolver;
    private final TaskProvider taskProvider;
    private final DomainCrawlerFactory domainCrawlerFactory;
    private final IpBlockList blockList;
    private final LinkedBlockingQueue<WorkerResults> queue;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public CrawlerDiscoverWorker(
            DomainCrawlerFactory domainCrawlerFactory, TaskProvider taskProvider, HttpRedirectResolver redirectResolver,
            IpBlockList blockList, LinkedBlockingQueue<WorkerResults> queue)
    {
        this.redirectResolver = redirectResolver;
        this.taskProvider = taskProvider;
        this.domainCrawlerFactory = domainCrawlerFactory;
        this.blockList = blockList;
        this.queue = queue;
    }

    @Override
    public void runCycle() throws InterruptedException {

        var ingress = taskProvider.getDiscoverTask();


        if (ingress.isEmpty()) {
            wmsa_edge_crawler_idle_worker.inc();
            Thread.sleep(1000);
            return;
        }

        try {
            if (ingress.rank > 0.25 && !blockList.isAllowed(ingress.domain)
            ) {
                logger.info("{} IP-blacklisted", ingress.domain);
                queue.put(new InvalidTaskResult(ingress.domain, "IP blacklisted"));
                return;
            }

            Optional<WorkerResults> results
                    = resolveRedirects(ingress);
            if (results.isPresent()) {
                queue.put(results.get());
                return;
            }

            long start = System.currentTimeMillis();

            var dc = domainCrawlerFactory.domainCrawler(ingress);
            var res = dc.crawl();

            wmsa_edge_crawler_thread_run_times.observe(System.currentTimeMillis() - start);

            queue.put(new DomainCrawlerWorkerResults(res));
        }
        catch (RuntimeException ex) {
            logger.warn("Leaking {}", ingress.domain);
            logger.error("Uncaught exception", ex);
        }
        catch (StackOverflowError er) {
            logger.error("Stack Overflow on  {}", ingress.domain);
            queue.put(new InvalidTaskResult(ingress.domain, "Stack overflow"));
        }
        catch (InterruptedException e) {
            logger.warn("ex", e);
        }
    }

    @Override
    public void run() {
        try {
            for (;;) {
                runCycle();
            }
        }
        catch (InterruptedException ex) {
            logger.error("Interrupted", ex);
        }
        catch (Throwable t) {
            logger.error("Fetcher thread terminating on uncaught exception", t);
            throw t;
        }
    }

    private Optional<WorkerResults> resolveRedirects(EdgeIndexTask ingress) {
        try {
            EdgeUrl firstUrl = ingress.urls.get(0);
            EdgeUrl homeUrl = new EdgeUrl(firstUrl.proto, firstUrl.domain, firstUrl.port, "/");

            EdgeUrl[] resolvedUrl = Observable.just(homeUrl)
                    .flatMap(url -> redirectResolver.probe(url).onErrorComplete())
                    .blockingStream().toArray(EdgeUrl[]::new);

            if (resolvedUrl.length == 0) {
                return Optional.of(new InvalidTaskResult(ingress.domain, "Failed to resolve redirect 1 @ " + ingress.urls.get(0)));
            }
            if (Objects.equals(resolvedUrl[0].domain, ingress.domain)) {
                return Optional.empty();
            }

            logger.debug("Aliased domain {} -> {}", ingress.domain, resolvedUrl[0].domain);

            return Optional.of(new DomainAliasResult(ingress.domain, resolvedUrl[0].domain, resolvedUrl));
        }
        catch (Exception ex) {
            logger.info("Could not alias ingress {}", ingress.domain);
        }
        return Optional.of(new InvalidTaskResult(ingress.domain, "Failed to resolve redirect 2"));
    }
}
