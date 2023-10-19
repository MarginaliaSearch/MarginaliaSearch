package nu.marginalia.actor.task;

import com.google.inject.Inject;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.svc.BackupService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.mq.persistence.MqPersistence;


public class RestoreBackupActor extends AbstractActorPrototype  {
    // States

    public static final String RESTORE = "RESTORE";
    public static final String END = "END";

    private final BackupService backupService;
    private final int node;
    private final MqPersistence mqPersistence;

    @Override
    public String describe() {
        return "Restores a backed up set of index data";
    }
    @Inject
    public RestoreBackupActor(ActorStateFactory stateFactory,
                              MqPersistence mqPersistence,
                              BackupService backupService,
                              ServiceConfiguration configuration
    ) {
        super(stateFactory);
        this.mqPersistence = mqPersistence;
        this.backupService = backupService;
        this.node = configuration.node();
    }

    @ActorState(name=RESTORE, next = END, resume = ActorResumeBehavior.ERROR)
    public void restoreBackup(FileStorageId id) throws Exception {
        backupService.restoreBackup(id);

        mqPersistence.sendNewMessage(
                ExecutorActor.CONVERT_AND_LOAD.id() + ":" + node,
                null,
                null,
                ConvertAndLoadActor.REPARTITION,
                "",
                null);
    }
}
