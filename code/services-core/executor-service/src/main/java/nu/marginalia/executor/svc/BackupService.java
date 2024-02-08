package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.RestoreBackupActor;
import nu.marginalia.executor.api.RpcFileStorageId;
import nu.marginalia.storage.model.FileStorageId;

public class BackupService {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public BackupService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public void restore(RpcFileStorageId request) throws Exception {
        var fid = FileStorageId.of(request.getFileStorageId());
        actorControlService.startFrom(ExecutorActor.RESTORE_BACKUP, new RestoreBackupActor.Restore(fid));
    }
}
