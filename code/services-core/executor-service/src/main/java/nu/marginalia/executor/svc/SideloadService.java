package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import nu.marginalia.executor.upload.UploadDirContents;
import nu.marginalia.executor.upload.UploadDirItem;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SideloadService {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public SideloadService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public Object sideloadDirtree(Request request, Response response) throws Exception {
         actorControlService.startFrom(ExecutorActor.CONVERT, new ConvertActor.ConvertDirtree(request.queryParams("path")));
         return "";
    }
    public Object sideloadWarc(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT, new ConvertActor.ConvertWarc(request.queryParams("path")));
        return "";
    }
    public Object sideloadEncyclopedia(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT,
                new ConvertActor.ConvertEncyclopedia(
                        request.queryParams("path"),
                        request.queryParams("baseUrl")
                        ));
        return "";
    }

    public Object sideloadStackexchange(Request request, Response response) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT, new ConvertActor.ConvertStackexchange(request.queryParams("path")));
        return "";
    }

    public UploadDirContents listUploadDir(Request request, Response response) throws IOException {
        Path uploadDir = WmsaHome.getUploadDir();

        try (var items = Files.list(uploadDir)) {
            return new UploadDirContents(uploadDir.toString(),
                    items.map(UploadDirItem::fromPath).toList());
        }

    }

}
