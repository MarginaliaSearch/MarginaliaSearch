package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.svc.BackupService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.mq.persistence.MqPersistence;


public class RestoreBackupActor extends RecordActorPrototype {
    private final BackupService backupService;
    private final int node;
    private final MqPersistence mqPersistence;

    public record Restore(FileStorageId fid) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Restore(FileStorageId fid) -> {

                backupService.restoreBackup(fid);

                mqPersistence.sendNewMessage(
                        ExecutorActor.CONVERT_AND_LOAD.id() + ":" + node,
                        null,
                        null,
                        ConvertAndLoadActor.REPARTITION,
                        "",
                        null);

                yield new End();
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Restores a backed up set of index data";
    }
    @Inject
    public RestoreBackupActor(Gson gson,
                              MqPersistence mqPersistence,
                              BackupService backupService,
                              ServiceConfiguration configuration
    ) {
        super(gson);
        this.mqPersistence = mqPersistence;
        this.backupService = backupService;
        this.node = configuration.node();
    }
}
