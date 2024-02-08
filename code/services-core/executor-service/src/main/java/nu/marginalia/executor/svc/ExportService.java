package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.*;
import nu.marginalia.executor.api.RpcExportSampleData;
import nu.marginalia.executor.api.RpcFileStorageId;
import nu.marginalia.storage.model.FileStorageId;

public class ExportService {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public ExportService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public void exportData() throws Exception {
         actorControlService.startFrom(ExecutorActor.EXPORT_DATA, new ExportDataActor.Export());
    }

    public void exportSampleData(RpcExportSampleData request) throws Exception {
        actorControlService.startFrom(ExecutorActor.EXPORT_SAMPLE_DATA,
                new ExportSampleDataActor.Export(
                    FileStorageId.of(request.getFileStorageId()),
                    request.getSize(),
                    request.getName()
            )
        );
    }

    public void exportAtags(RpcFileStorageId request) throws Exception {
        actorControlService.startFrom(ExecutorActor.EXPORT_ATAGS,
                new ExportAtagsActor.Export(FileStorageId.of(request.getFileStorageId()))
        );
    }

    public void exportFeeds(RpcFileStorageId request) throws Exception {
        actorControlService.startFrom(ExecutorActor.EXPORT_FEEDS,
                new ExportFeedsActor.Export(FileStorageId.of(request.getFileStorageId()))
        );
    }

    public void exportTermFrequencies(RpcFileStorageId request) throws Exception {
        actorControlService.startFrom(ExecutorActor.EXPORT_TERM_FREQUENCIES,
                new ExportTermFreqActor.Export(FileStorageId.of(request.getFileStorageId()))
        );
    }

}
