package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.process.ProcessService;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.module.ServiceConfiguration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class ProcessLivenessMonitorActor extends RecordActorPrototype {

    private final ServiceEventLog eventLogService;
    private final ProcessService processService;
    private final HikariDataSource dataSource;

    private final int node;
    public record Initial() implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RESTART)
    public record Monitor() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial() -> new Monitor();
            case Monitor() -> {
                for (;;) {
                    for (var heartbeat : getProcessHeartbeats()) {
                        if (!heartbeat.isRunning()) continue;

                        var processId = heartbeat.getProcessId();
                        if (null == processId) continue;

                        if (processService.isRunning(processId) && heartbeat.lastSeenMillis() < 10_000)
                            continue;

                        flagProcessAsStopped(heartbeat);
                    }

                    for (var heartbeat : getTaskHeartbeats()) {
                        if (heartbeat.lastSeenMillis() < 10_000) continue;

                        removeTaskHeartbeat(heartbeat);
                    }

                    TimeUnit.SECONDS.sleep(60);
                }
            }
            default -> new Error();
        };
    }

    @Inject
    public ProcessLivenessMonitorActor(Gson gson,
                                       ServiceEventLog eventLogService,
                                       ServiceConfiguration configuration,
                                       ProcessService processService,
                                       HikariDataSource dataSource) {
        super(gson);
        this.node = configuration.node();
        this.eventLogService = eventLogService;
        this.processService = processService;
        this.dataSource = dataSource;
    }

    @Override
    public String describe() {
        return "Periodically check to ensure that the control service's view of running processes is agreement with the process heartbeats table.";
    }

    private List<ProcessHeartbeat> getProcessHeartbeats() {
        List<ProcessHeartbeat> heartbeats = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT PROCESS_NAME, PROCESS_BASE, INSTANCE, STATUS, PROGRESS,
                            TIMESTAMPDIFF(MICROSECOND, HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM PROCESS_HEARTBEAT
                    WHERE NODE = ?
                     """)) {

            stmt.setInt(1, node);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                int progress = rs.getInt("PROGRESS");
                heartbeats.add(new ProcessHeartbeat(
                        rs.getString("PROCESS_NAME"),
                        rs.getString("PROCESS_BASE"),
                        rs.getString("INSTANCE"),
                        rs.getLong("TSDIFF") / 1000.,
                        progress < 0 ? null : progress,
                        rs.getString("STATUS")
                ));
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return heartbeats;
    }

    private void flagProcessAsStopped(ProcessHeartbeat processHeartbeat) {
        eventLogService.logEvent("PROCESS-MISSING", "Marking stale process heartbeat "
                + processHeartbeat.processId() + " / " + processHeartbeat.uuidFull() + " as stopped");

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     UPDATE PROCESS_HEARTBEAT
                        SET STATUS = 'STOPPED'
                      WHERE INSTANCE = ?
                     """)) {

            stmt.setString(1, processHeartbeat.uuidFull());
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }


    private List<TaskHeartbeat> getTaskHeartbeats() {
        List<TaskHeartbeat> heartbeats = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT TASK_NAME, TASK_BASE, INSTANCE, SERVICE_INSTANCE,  STATUS, STAGE_NAME, PROGRESS, TIMESTAMPDIFF(MICROSECOND, TASK_HEARTBEAT.HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM TASK_HEARTBEAT
                    WHERE NODE=?
                     """)) {
            stmt.setInt(1, node);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                int progress = rs.getInt("PROGRESS");
                heartbeats.add(new TaskHeartbeat(
                        rs.getString("TASK_NAME"),
                        rs.getString("TASK_BASE"),
                        rs.getString("INSTANCE"),
                        rs.getString("SERVICE_INSTANCE"),
                        rs.getLong("TSDIFF") / 1000.,
                        progress < 0 ? null : progress,
                        rs.getString("STAGE_NAME"),
                        rs.getString("STATUS")
                ));
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return heartbeats;
    }

    private void removeTaskHeartbeat(TaskHeartbeat heartbeat) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM TASK_HEARTBEAT
                     WHERE INSTANCE = ?
                     """)) {

            stmt.setString(1, heartbeat.instanceUuidFull());
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private record ProcessHeartbeat(
            String processId,
            String processBase,
            String uuidFull,
            double lastSeenMillis,
            Integer progress,
            String status
    ) {
        public boolean isRunning() {
            return "RUNNING".equals(status);
        }
        public ProcessService.ProcessId getProcessId() {
            return switch (processBase) {
                case "converter" -> ProcessService.ProcessId.CONVERTER;
                case "crawler" -> ProcessService.ProcessId.CRAWLER;
                case "loader" -> ProcessService.ProcessId.LOADER;
                case "website-adjacencies-calculator" -> ProcessService.ProcessId.ADJACENCIES_CALCULATOR;
                case "index-constructor" -> ProcessService.ProcessId.INDEX_CONSTRUCTOR;
                default -> null;
            };
        }
    }

    private record TaskHeartbeat(
            String taskName,
            String taskBase,
            String instanceUuidFull,
            String serviceUuuidFull,
            double lastSeenMillis,
            Integer progress,
            String stage,
            String status
    ) { }

}
