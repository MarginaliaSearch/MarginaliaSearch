package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.ActorTimeslot;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorStateMachines;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.actor.task.ExportFeedsActor;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public class ScheduledMaintenanceActor extends RecordActorPrototype {
    private static final Logger logger = LoggerFactory.getLogger(ScheduledMaintenanceActor.class);

    private final NodeConfigurationService nodeConfigurationService;
    private final ExecutorActorStateMachines executorStateMachines;
    private final FileStorageService fileStorageService;

    private final MqPersistence persistence;
    private final ServiceEventLog eventLog;

    private final ActorTimeslot.ActorSchedule schedule = ActorTimeslot.MAINTENANCE_SLOT;
    private final int nodeId;


    @Inject
    public ScheduledMaintenanceActor(Gson gson,
                                     MqPersistence persistence,
                                     ServiceConfiguration serviceConfiguration,
                                     ExecutorActorStateMachines executorStateMachines,
                                     FileStorageService fileStorageService,
                                     NodeConfigurationService nodeConfigurationService, ServiceEventLog eventLog) throws SQLException {
        super(gson);
        this.persistence = persistence;
        this.nodeId = serviceConfiguration.node();
        this.executorStateMachines = executorStateMachines;
        this.fileStorageService = fileStorageService;
        this.nodeConfigurationService = nodeConfigurationService;
        this.eventLog = eventLog;
    }

    public record Initial() implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record Run() implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Wait(String startTs, String endTs) implements ActorStep {
        public Wait(ActorTimeslot timeslot) {
            this(timeslot.start().toString(), timeslot.end().toString());
        }
    }
    public record WaitMsg(long msgId) implements ActorStep { }


    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record Task_CleanOldExports() implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record Task_CleanOldBackups() implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record Task_FetchRSSFeeds() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        var nodeConfiguration = nodeConfigurationService.get(nodeId);
        final String taskName = self.getClass().getSimpleName();

        return switch (self) {
            case Initial() -> {
                var profile = nodeConfiguration.profile();

                if (profile != NodeProfile.BATCH_CRAWL && profile != NodeProfile.MIXED) {
                    yield new End();
                }

                yield new Wait(schedule.nextTimeslot());
            }

            case Wait(String startTs, String endTs) -> {
                var start = Instant.parse(startTs);
                var end = Instant.parse(endTs);
                var now = Instant.now();

                Thread.sleep(Duration.between(now, start));

                yield switch ((LocalDate.now().getDayOfMonth() + nodeId) % 31) {
                        case 13, 25 -> new Task_FetchRSSFeeds();
                        case 1 -> new Task_CleanOldExports();
                        case 2 -> new Task_CleanOldBackups();
                        default -> new Wait(schedule.nextTimeslot());
                };
            }

            case Task_CleanOldExports() -> {
                if (!nodeConfiguration.autoClean()) {
                    eventLog.logEvent("MAIN-TASK-SKIPPED", taskName);
                    yield new Wait(schedule.nextTimeslot());
                }

                LocalDateTime cutoff = LocalDateTime.now().minusMonths(3);

                for (var storage : fileStorageService.getEachFileStorage(FileStorageType.EXPORT)) {
                    if (storage.createDateTime().isAfter(cutoff))
                        continue;

                    fileStorageService.flagFileForDeletion(storage.id());
                }

                eventLog.logEvent("MAINT-TASK-OK", taskName);
                yield new Wait(schedule.nextTimeslot());
            }

            case Task_CleanOldBackups() -> {
                if (!nodeConfiguration.autoClean()) {
                    eventLog.logEvent("MAIN-TASK-SKIPPED", taskName);
                    yield new Wait(schedule.nextTimeslot());
                }

                LocalDateTime cutoff = LocalDateTime.now().minusMonths(3);

                for (var storage : fileStorageService.getEachFileStorage(FileStorageType.BACKUP)) {
                    if (storage.createDateTime().isAfter(cutoff))
                        continue;

                    fileStorageService.flagFileForDeletion(storage.id());
                }

                eventLog.logEvent("MAINT-TASK-OK", taskName);

                yield new Wait(schedule.nextTimeslot());
            }
            case Task_FetchRSSFeeds() -> {
                if (!executorStateMachines.get(ExecutorActor.CRAWL).getState().isFinal()) {
                    eventLog.logEvent("MAIN-TASK-SKIPPED", taskName);
                    yield new Wait(schedule.nextTimeslot());
                }

                Optional<FileStorageId> storageId = fileStorageService.getOnlyActiveFileStorage(FileStorageType.CRAWL_DATA);
                if (storageId.isEmpty()) {
                    eventLog.logEvent("MAIN-TASK-SKIPPED", taskName);
                    yield new Wait(schedule.nextTimeslot());
                }

                long msgId = createTrackingTokenMsg(taskName, nodeId, Duration.ofHours(3));
                executorStateMachines.initFrom(ExecutorActor.EXPORT_FEEDS,
                        new ExportFeedsActor.Export(msgId, storageId.get())
                );

                yield new Wait(schedule.nextTimeslot());
            }
            case WaitMsg(long msgId) -> {
                var msg = persistence.waitForMessageTerminalState(msgId, Duration.ofSeconds(15), Duration.ofHours(24));
                if (msg.state() != MqMessageState.OK) {
                    eventLog.logEvent("MAINT-TASK-FAILED", msg.function());
                }
                else {
                    eventLog.logEvent("MAINT-TASK-OK", msg.function());
                }
                yield new Wait(schedule.nextTimeslot());
            }
            case End() -> {
                yield new End(); // will not loop, terminal state
            }
            default -> new Error("Unknown actor step: " + self);
        };
    }

    private long createTrackingTokenMsg(String task, int node, Duration ttl) throws Exception {
        return persistence.sendNewMessage("task-tracking[" + node + "]",
                "maintenance-actor", null, task, "", ttl);
    }

    @Override
    public String describe() {
        return "Runs scheduled maintenance jobs";
    }
}
