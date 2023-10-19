package nu.marginalia.executor.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import nu.marginalia.actor.task.ConvertAndLoadActor;
import nu.marginalia.actor.task.CrawlJobExtractorActor;
import nu.marginalia.actor.task.RecrawlActor;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.executor.model.load.LoadParameters;
import spark.Request;
import spark.Response;

public class ProcessingService {
    private final ExecutorActorControlService actorControlService;
    private final Gson gson;

    @Inject
    public ProcessingService(ExecutorActorControlService actorControlService,
                             Gson gson) {
        this.actorControlService = actorControlService;
        this.gson = gson;
    }

    public Object startRecrawl(Request request, Response response) throws Exception {
        var crawlId = gson.fromJson(request.body(), FileStorageId.class);

        actorControlService.start(
                ExecutorActor.RECRAWL,
                RecrawlActor.recrawlFromCrawlDataAndCrawlSpec(crawlId)
        );

        return "";
    }

    public Object startCrawl(Request request, Response response) throws Exception {
        actorControlService.start(ExecutorActor.CRAWL, FileStorageId.parse(request.params("fid")));

        return "";
    }

    public Object startConversion(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT, ConvertActor.CONVERT, FileStorageId.parse(request.params("fid")));

        return "";
    }

    public Object startConvertLoad(Request request, Response response) throws Exception {
        actorControlService.start(
                ExecutorActor.CONVERT_AND_LOAD,
                FileStorageId.parse(request.params("fid"))
        );
        return "";
    }


    public Object startLoad(Request request, Response response) throws Exception {
        var params = gson.fromJson(request.body(), LoadParameters.class);

        // Start the FSM from the intermediate state that triggers the load
        actorControlService.startFrom(
                ExecutorActor.CONVERT_AND_LOAD,
                ConvertAndLoadActor.LOAD,
                new ConvertAndLoadActor.Message(null, params.ids(),
                        0L,
                        0L)
        );

        return "";
    }

    public Object startAdjacencyCalculation(Request request, Response response) throws Exception {
        actorControlService.start(ExecutorActor.ADJACENCY_CALCULATION);
        return "";
    }

    public Object createCrawlSpecFromDownload(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CRAWL_JOB_EXTRACTOR, CrawlJobExtractorActor.CREATE_FROM_LINK,
                new CrawlJobExtractorActor.CrawlJobExtractorArgumentsWithURL(
                        request.queryParamOrDefault("description", ""),
                        request.queryParamOrDefault("url", ""))
        );
        return "";
    }
}
