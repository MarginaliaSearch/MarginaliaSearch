package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.*;
import nu.marginalia.executor.api.RpcCrawlSpecFromDownload;
import nu.marginalia.executor.api.RpcFileStorageId;
import nu.marginalia.executor.api.RpcFileStorageIds;
import nu.marginalia.storage.model.FileStorageId;

import java.util.stream.Collectors;

public class ProcessingService {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public ProcessingService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public void startRecrawl(RpcFileStorageId request) throws Exception {
        actorControlService.startFrom(ExecutorActor.RECRAWL,
                new RecrawlActor.Initial(FileStorageId.of(request.getFileStorageId()), false));
    }

    public void startCrawl(RpcFileStorageId request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CRAWL,
                new CrawlActor.Initial(FileStorageId.of(request.getFileStorageId())));
    }

    public void startConversion(RpcFileStorageId request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT,
                new ConvertActor.Convert(FileStorageId.of(request.getFileStorageId())));
    }

    public void startConvertLoad(RpcFileStorageId request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT_AND_LOAD,
                new ConvertAndLoadActor.Initial(FileStorageId.of(request.getFileStorageId())));
    }

    public void startLoad(RpcFileStorageIds request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT_AND_LOAD,
                new ConvertAndLoadActor.Load(request.getFileStorageIdsList()
                        .stream()
                            .map(FileStorageId::of)
                            .collect(Collectors.toList()))
        );
    }

    public void startAdjacencyCalculation() throws Exception {
        actorControlService.startFrom(ExecutorActor.ADJACENCY_CALCULATION, new TriggerAdjacencyCalculationActor.Run());
    }

    public void createCrawlSpecFromDownload(RpcCrawlSpecFromDownload request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CRAWL_JOB_EXTRACTOR,
                new CrawlJobExtractorActor.CreateFromUrl(
                        request.getDescription(),
                        request.getUrl())
        );
    }
}
