package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.actor.Actor;
import nu.marginalia.actor.ActorControlService;
import nu.marginalia.actor.task.RestoreBackupActor;
import nu.marginalia.storage.model.FileStorageId;
import spark.Request;
import spark.Response;

public class BackupService {
    private final ActorControlService actorControlService;

    @Inject
    public BackupService(ActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public Object restore(Request request, Response response) throws Exception {
        var fid = FileStorageId.parse(request.params("fid"));
        actorControlService.startFrom(Actor.RESTORE_BACKUP, RestoreBackupActor.RESTORE, fid);
        return "";
    }
}
