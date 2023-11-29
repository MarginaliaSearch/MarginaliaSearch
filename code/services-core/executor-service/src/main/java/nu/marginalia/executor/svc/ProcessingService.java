package nu.marginalia.executor.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.*;
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

        actorControlService.startFrom(
                ExecutorActor.RECRAWL,
                new RecrawlActor.Initial(crawlId)
        );

        return "";
    }

    public Object startCrawl(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CRAWL,
                new CrawlActor.Initial(FileStorageId.parse(request.params("fid"))));

        return "";
    }

    public Object startConversion(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT,
                new ConvertActor.Convert(FileStorageId.parse(request.params("fid"))));

        return "";
    }

    public Object startConvertLoad(Request request, Response response) throws Exception {
        actorControlService.startFrom(
                ExecutorActor.CONVERT_AND_LOAD,
                new ConvertAndLoadActor.Initial(FileStorageId.parse(request.params("fid")))
        );

        return "";
    }


    public Object startLoad(Request request, Response response) throws Exception {
        var params = gson.fromJson(request.body(), LoadParameters.class);

        // Start the FSM from the intermediate state that triggers the load
        actorControlService.startFrom(
                ExecutorActor.CONVERT_AND_LOAD,
                new ConvertAndLoadActor.Load(params.ids())
        );

        return "";
    }

    public Object startAdjacencyCalculation(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.ADJACENCY_CALCULATION, new TriggerAdjacencyCalculationActor.Run());
        return "";
    }

    public Object createCrawlSpecFromDownload(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CRAWL_JOB_EXTRACTOR,
                new CrawlJobExtractorActor.CreateFromUrl(
                        request.queryParamOrDefault("description", ""),
                        request.queryParamOrDefault("url", ""))
        );
        return "";
    }
}
