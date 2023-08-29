package nu.marginalia.control.actor.task;

import com.google.inject.Inject;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.control.actor.Actor;
import nu.marginalia.control.svc.BackupService;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.mq.persistence.MqPersistence;


public class RestoreBackupActor extends AbstractActorPrototype  {
    // States

    public static final String RESTORE = "RESTORE";
    public static final String END = "END";

    private final BackupService backupService;
    private final MqPersistence mqPersistence;

    @Override
    public String describe() {
        return "Restores a backed up set of index data";
    }
    @Inject
    public RestoreBackupActor(ActorStateFactory stateFactory,
                              MqPersistence mqPersistence,
                              BackupService backupService
    ) {
        super(stateFactory);
        this.mqPersistence = mqPersistence;
        this.backupService = backupService;
    }

    @ActorState(name=RESTORE, next = END, resume = ActorResumeBehavior.ERROR)
    public void restoreBackup(FileStorageId id) throws Exception {
        backupService.restoreBackup(id);

        mqPersistence.sendNewMessage(
                Actor.CONVERT_AND_LOAD.id(),
                null,
                null,
                ConvertAndLoadActor.REPARTITION,
                "",
                null);
    }
}
