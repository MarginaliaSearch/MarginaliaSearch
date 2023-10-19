package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.RestoreBackupActor;
import nu.marginalia.storage.model.FileStorageId;
import spark.Request;
import spark.Response;

public class BackupService {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public BackupService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public Object restore(Request request, Response response) throws Exception {
        var fid = FileStorageId.parse(request.params("fid"));
        actorControlService.startFrom(ExecutorActor.RESTORE_BACKUP, RestoreBackupActor.RESTORE, fid);
        return "";
    }
}
