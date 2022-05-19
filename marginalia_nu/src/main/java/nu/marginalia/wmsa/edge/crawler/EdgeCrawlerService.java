package nu.marginalia.wmsa.edge.crawler;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.data_store.client.DataStoreClient;
import nu.marginalia.wmsa.edge.crawler.worker.UploaderWorker;
import nu.marginalia.wmsa.edge.crawler.worker.Worker;
import nu.marginalia.wmsa.edge.crawler.worker.WorkerFactory;
import nu.marginalia.wmsa.edge.crawler.worker.data.CrawlJobsSpecification;
import nu.marginalia.wmsa.edge.crawler.worker.results.WorkerResults;
import nu.marginalia.wmsa.edge.director.client.EdgeDirectorClient;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class EdgeCrawlerService extends Service {
    private final EdgeIndexClient indexClient;
    private final EdgeDirectorClient directorClient;
    private final Initialization init;
    private final WorkerFactory workerFactory;
    private final CrawlJobsSpecificationSet specifications;
    private final DataStoreClient dataStoreClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private UploaderWorker uploader;
    private final List<Worker> crawlers = new ArrayList<>();

    @Inject
    public EdgeCrawlerService(@Named("service-host") String ip,
                              @Named("service-port") Integer port,
                              DataStoreClient dataStoreClient,
                              EdgeIndexClient indexClient,
                              EdgeDirectorClient directorClient,
                              Initialization init,
                              WorkerFactory workerFactory,
                              CrawlJobsSpecificationSet specifications,
                              Initialization initialization,
                              MetricsServer metricsServer
                              ) {
        super(ip, port, initialization, metricsServer);
        this.dataStoreClient = dataStoreClient;
        this.indexClient = indexClient;
        this.directorClient = directorClient;
        this.init = init;
        this.workerFactory = workerFactory;
        this.specifications = specifications;

        Schedulers.newThread().scheduleDirect(this::run);

    }

    @SneakyThrows
    private void run() {
        init.waitReady();
        indexClient.waitReady();
        directorClient.waitReady();
        dataStoreClient.waitReady();

        directorClient.flushOngoingJobs(Context.internal());

        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("https://memex.marginalia.nu/")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("http://www.cs.uni.edu/")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("https://www.leonardcohenfiles.com/")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("http://atsf.railfan.net/")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("http://sprott.physics.wisc.edu/")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("http://www.attalus.org/")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("http://www.attalus.org/")).blockingSubscribe();

        final List<LinkedBlockingQueue<WorkerResults>> queues = new ArrayList<>(specifications.size());

        for (int i = 0; i < specifications.size(); i++) {
            queues.add(new LinkedBlockingQueue<>(1));
        }

        for (int i = 0; i < specifications.size(); i++) {
            var spec = specifications.get(i);
            var queue = queues.get(i);

            Worker worker;
            if (spec.pass == 0) {
                worker = workerFactory.buildDiscoverWorker(queue);
            }
            else {
                worker = workerFactory.buildIndexWorker(queue, spec.pass);
            }

            crawlers.add(worker);

            new Thread(worker, "Fetcher-"+i).start();
        }

        uploader = workerFactory.buildUploader(queues);
        new Thread(uploader, "Uploader").start();
    }

}
