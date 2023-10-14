package nu.marginalia.executor.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.Actor;
import nu.marginalia.actor.ActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import nu.marginalia.actor.task.ConvertAndLoadActor;
import nu.marginalia.actor.task.CrawlJobExtractorActor;
import nu.marginalia.actor.task.RecrawlActor;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.executor.model.crawl.RecrawlParameters;
import nu.marginalia.executor.model.load.LoadParameters;
import spark.Request;
import spark.Response;

public class ProcessingService {
    private final ActorControlService actorControlService;
    private final Gson gson;

    @Inject
    public ProcessingService(ActorControlService actorControlService,
                             Gson gson) {
        this.actorControlService = actorControlService;
        this.gson = gson;
    }

    public Object startRecrawl(Request request, Response response) throws Exception {
        var params = gson.fromJson(request.body(), RecrawlParameters.class);

        actorControlService.start(
                Actor.RECRAWL,
                RecrawlActor.recrawlFromCrawlDataAndCralSpec(
                        params.crawlDataId(),
                        params.crawlSpecIds()
                )
        );

        return "";
    }

    public Object startCrawl(Request request, Response response) throws Exception {
        actorControlService.start(Actor.CRAWL, FileStorageId.parse(request.params("fid")));

        return "";
    }

    public Object startConversion(Request request, Response response) throws Exception {
        actorControlService.startFrom(Actor.CONVERT, ConvertActor.CONVERT, FileStorageId.parse(request.params("fid")));

        return "";
    }

    public Object startConvertLoad(Request request, Response response) throws Exception {
        actorControlService.start(
                Actor.CONVERT_AND_LOAD,
                FileStorageId.parse(request.params("fid"))
        );
        return "";
    }


    public Object startLoad(Request request, Response response) throws Exception {
        var params = gson.fromJson(request.body(), LoadParameters.class);

        // Start the FSM from the intermediate state that triggers the load
        actorControlService.startFrom(
                Actor.CONVERT_AND_LOAD,
                ConvertAndLoadActor.LOAD,
                new ConvertAndLoadActor.Message(null, params.ids(),
                        0L,
                        0L)
        );

        return "";
    }

    public Object startAdjacencyCalculation(Request request, Response response) throws Exception {
        actorControlService.start(Actor.ADJACENCY_CALCULATION);
        return "";
    }

    public Object createCrawlSpecFromDb(Request request, Response response) throws Exception {
        actorControlService.startFrom(Actor.CRAWL_JOB_EXTRACTOR, CrawlJobExtractorActor.CREATE_FROM_DB,
                new CrawlJobExtractorActor.CrawlJobExtractorArguments(
                        request.queryParamOrDefault("description", ""))
                );
        return "";
    }

    public Object createCrawlSpecFromDownload(Request request, Response response) throws Exception {
        actorControlService.startFrom(Actor.CRAWL_JOB_EXTRACTOR, CrawlJobExtractorActor.CREATE_FROM_LINK,
                new CrawlJobExtractorActor.CrawlJobExtractorArgumentsWithURL(
                        request.queryParamOrDefault("description", ""),
                        request.queryParamOrDefault("url", ""))
        );
        return "";
    }
}
