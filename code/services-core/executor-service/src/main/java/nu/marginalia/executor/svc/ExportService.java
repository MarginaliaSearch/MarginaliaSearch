package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import nu.marginalia.actor.task.ExportAtagsActor;
import nu.marginalia.actor.task.ExportDataActor;
import nu.marginalia.storage.model.FileStorageId;
import spark.Request;
import spark.Response;

public class ExportService {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public ExportService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public Object exportData(Request request, Response response) throws Exception {
         actorControlService.startFrom(ExecutorActor.EXPORT_DATA, new ExportDataActor.Export());
         return "";
    }

    public Object exportAtags(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.EXPORT_ATAGS, new ExportAtagsActor.Export(FileStorageId.parse(request.queryParams("fid"))));
        return "";
    }

    public Object exportFeeds(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.EXPORT_FEEDS, new ExportAtagsActor.Export(FileStorageId.parse(request.queryParams("fid"))));
        return "";
    }
    public Object exportTermFrequencies(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.EXPORT_TERM_FREQUENCIES, new ExportAtagsActor.Export(FileStorageId.parse(request.queryParams("fid"))));
        return "";
    }


}
