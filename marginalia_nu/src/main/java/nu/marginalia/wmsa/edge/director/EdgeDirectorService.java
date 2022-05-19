package nu.marginalia.wmsa.edge.director;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.prometheus.client.Histogram;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreTaskDao;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import org.eclipse.jetty.util.UrlEncoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import static spark.Spark.*;

public class EdgeDirectorService extends Service {
    private final Gson gson = new GsonBuilder().create();
    private final EdgeDataStoreTaskDao taskDao;

    static final Histogram request_time_metrics
            = Histogram.build("wmsa_edge_director_request_time", "DB Request Time")
            .labelNames("request")
            .register();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public EdgeDirectorService(@Named("service-host") String ip,
                               @Named("service-port") Integer port,
                               Initialization init,
                               EdgeDataStoreTaskDao taskDao,
                               MetricsServer metricsServer)
    {
        super(ip, port, init, metricsServer);
        this.taskDao = taskDao;


        Spark.path("edge", () -> {
            get("/task/index/:pass", this::getIndexTask, this::convertToJson);
            get("/task/discover/", this::getDiscoverTask, this::convertToJson);
            delete("/task/*", this::finishTask, this::convertToJson);
            get("/task/blocked", this::isBlocked, this::convertToJson);
            post("/task/flush", this::flushTasks, this::convertToJson);
        });

    }

    private Object flushTasks(Request request, Response response) {
        logger.info("Flushing ongoing jobs");
        taskDao.flushOngoingJobs();
        return "Ok";
    }

    private Object isBlocked(Request request, Response response) {
        return taskDao.isBlocked();
    }

    public Object getIndexTask(Request request, Response response) {
        final long start = System.currentTimeMillis();

        response.header("Content-Encoding", "gzip");
        var ret = taskDao.getIndexTask(Integer.parseInt(request.params("pass")), Integer.parseInt(request.queryParams("limit")));

        request_time_metrics.labels("get_index_task").observe(System.currentTimeMillis() - start);

        return ret;
    }

    public Object getDiscoverTask(Request request, Response response) {
        final long start = System.currentTimeMillis();

        response.header("Content-Encoding", "gzip");
        var ret = taskDao.getDiscoverTask();

        request_time_metrics.labels("get_discover_task").observe(System.currentTimeMillis() - start);

        return ret;
    }

    public Object finishTask(Request request, Response response) {
        final long start = System.currentTimeMillis();

        var domain = UrlEncoded.decodeString(request.splat()[0]);
        EdgeDomainIndexingState state = EdgeDomainIndexingState.valueOf(request.queryParams("state"));

        if (state.code < 0) {
            taskDao.finishBadIndexTask(new EdgeDomain(domain), state);
        }
        else {
            double quality = Double.parseDouble(request.queryParams("quality"));
            taskDao.finishIndexTask(new EdgeDomain(domain), quality, state);
        }

        request_time_metrics.labels("finish_task").observe(System.currentTimeMillis() - start);
        return null;
    }


    private String convertToJson(Object o) {
        return gson.toJson(o);
    }
}
