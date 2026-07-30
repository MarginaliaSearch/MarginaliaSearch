package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorTimeslot;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.pq.PersistentQueryResultDb;
import nu.marginalia.pq.PersistentQuerySpec;
import nu.marginalia.pq.PersistentQuerySpecsDb;
import nu.marginalia.schedule.ActorScheduleRow;
import nu.marginalia.schedule.ActorScheduleService;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Singleton
public class PersistentQueryActor extends RecordActorPrototype {

    private static final Logger logger = LoggerFactory.getLogger(PersistentQueryActor.class);

    private final QueryClient queryClient;
    private final FileStorageService fileStorageService;
    private final ServiceHeartbeat serviceHeartbeat;
    private final ActorScheduleService scheduleService;
    private final PersistentQuerySpecsDb specsDb;

    private final int nodeId;

    public record Initial() implements ActorStep {}

    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Monitor(String runTimeTs) implements ActorStep {
        public Monitor(ActorTimeslot timeslot) {
            this(timeslot.start().toString());
        }
    }

    @Resume(behavior = ActorResumeBehavior.RESTART)
    public record Run() implements ActorStep {

    }

    private ActorTimeslot nextTimeslot() {
        return new ActorTimeslot.ActorSchedule(scheduleService.getTrigger(ActorScheduleRow.Trigger.LIVE_CRAWLER)).nextTimeslot();
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Initial() -> {
                // Ensure we have an active file storage
                var storages = fileStorageService.getActiveFileStorages(nodeId,
                        FileStorageType.PERSISTENT_QUERY);

                if (storages.size() > 1) {
                    yield new Error("Illegal state, multiple active file storages for type PERSISTENT_QUERY");
                }

                if (storages.isEmpty()) {
                    var fs = fileStorageService.allocateStorage(FileStorageType.PERSISTENT_QUERY,
                            "persistent-query", "Persistent Query Storage");
                    fileStorageService.setFileStorageState(fs.id(), FileStorageState.ACTIVE);
                }

                yield new Monitor(nextTimeslot());
            }

            case Monitor(String runTimeTs) -> {
                Instant checkTime = Instant.parse(runTimeTs);
                Thread.sleep(Duration.between(Instant.now(), checkTime));

                yield new Run();
            }

            case Run() -> {
                var storageId = fileStorageService.getOnlyActiveFileStorage(FileStorageType.PERSISTENT_QUERY);
                if (storageId.isEmpty()) {
                    yield new Error("No active file storage of type PERSISTENT_QUERIES");
                }
                FileStorage storage = fileStorageService.getStorage(storageId.get());

                var specs = specsDb.getEnabledQueries();
                try (var hb = serviceHeartbeat.createServiceAdHocTaskHeartbeat("Persistent Queries")) {
                    for (var spec: hb.wrap("Querying", specs)) {
                        runQuery(storage, spec);
                    }
                }
                yield new Monitor(nextTimeslot());
            }

            default -> {
                yield new Error("Bad state");
            }
        };
    }

    private void runQuery(FileStorage storage, PersistentQuerySpec spec) throws SQLException, InterruptedException {
        try (var db = new PersistentQueryResultDb(storage.asPath(), spec)) {
            String cursor = null;
            int errors = 0;

            Instant queryTime = Instant.now();
            int discoveries = 0;

            for (;;) {
                try {
                    var res = queryClient.unrankedSearch(
                            spec.termsInclude(),
                            spec.termsExclude(),
                            spec.languageIsoCode(),
                            RpcQueryLimits.newBuilder()
                                    .setResultsTotal(100)
                                    .setTimeoutMs(250)
                                    .build(),
                            cursor
                    );

                    for (var result: res.results()) {
                        if (db.addResult(result.url, result.title, queryTime)) {
                            discoveries++;
                        }
                    }

                    // FIXME: res.hasMore() is broken
                    if (res.results().isEmpty()) {
                        break;
                    }

                    cursor = res.encodedCursor();
                }
                catch (Exception ex) {
                    logger.warn("Error in executing persistent query", ex);

                    errors++;

                    if (errors > 5) { // FIXME
                        logger.error("Failed to execute query after several errors", ex);
                        break;
                    }
                }

                // Add some grace sleep to not absolutely nuke the index
                Thread.sleep(Duration.ofMillis(150));
            }

            if (discoveries != 0) {
                db.addSummary(queryTime, discoveries);
            }
        }
    }

    @Override
    public String describe() {
        return "Executes persistent queries";
    }

    @Inject
    public PersistentQueryActor(Gson gson,
                                ServiceConfiguration configuration,
                                QueryClient queryClient,
                                FileStorageService fileStorageService,
                                ServiceHeartbeat serviceHeartbeat,
                                ActorScheduleService scheduleService, PersistentQuerySpecsDb specsDb)
    {
        super(gson);
        this.nodeId = configuration.node();
        this.queryClient = queryClient;
        this.fileStorageService = fileStorageService;
        this.serviceHeartbeat = serviceHeartbeat;
        this.scheduleService = scheduleService;
        this.specsDb = specsDb;
    }

}
