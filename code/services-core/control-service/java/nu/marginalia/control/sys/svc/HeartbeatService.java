package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.sys.model.ProcessHeartbeat;
import nu.marginalia.control.sys.model.ServiceHeartbeat;
import nu.marginalia.control.sys.model.TaskHeartbeat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class HeartbeatService {
    private final HikariDataSource dataSource;

    @Inject
    public HeartbeatService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<ServiceHeartbeat> getServiceHeartbeats() {
        List<ServiceHeartbeat> heartbeats = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT SERVICE_NAME, SERVICE_BASE, INSTANCE, ALIVE,
                            TIMESTAMPDIFF(MICROSECOND, HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM SERVICE_HEARTBEAT
                     """)) {

            var rs = stmt.executeQuery();
            while (rs.next()) {
                heartbeats.add(new ServiceHeartbeat(
                        rs.getString("SERVICE_NAME"),
                        rs.getString("SERVICE_BASE"),
                        rs.getString("INSTANCE"),
                        rs.getLong("TSDIFF") / 1000.,
                        rs.getBoolean("ALIVE")
                ));
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return heartbeats;
    }

     public List<TaskHeartbeat> getTaskHeartbeats() {
        List<TaskHeartbeat> heartbeats = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT TASK_NAME, TASK_BASE, NODE, INSTANCE, SERVICE_INSTANCE,  STATUS, STAGE_NAME, PROGRESS, TIMESTAMPDIFF(MICROSECOND, TASK_HEARTBEAT.HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM TASK_HEARTBEAT
                    WHERE STATUS = 'RUNNING'
                     """)) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                int progress = rs.getInt("PROGRESS");
                heartbeats.add(new TaskHeartbeat(
                        rs.getString("TASK_NAME"),
                        rs.getString("TASK_BASE"),
                        rs.getInt("NODE"),
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

    public List<TaskHeartbeat> getTaskHeartbeatsForNode(int node) {
        List<TaskHeartbeat> heartbeats = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT TASK_NAME, TASK_BASE, NODE, INSTANCE, SERVICE_INSTANCE,  STATUS, STAGE_NAME, PROGRESS, TIMESTAMPDIFF(MICROSECOND, TASK_HEARTBEAT.HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM TASK_HEARTBEAT
                    WHERE NODE=?
                    AND STATUS='RUNNING'
                     """)) {
            stmt.setInt(1, node);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                int progress = rs.getInt("PROGRESS");
                heartbeats.add(new TaskHeartbeat(
                        rs.getString("TASK_NAME"),
                        rs.getString("TASK_BASE"),
                        rs.getInt("NODE"),
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

    public void removeTaskHeartbeat(TaskHeartbeat heartbeat) {
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

    public List<ProcessHeartbeat> getProcessHeartbeats() {
        List<ProcessHeartbeat> heartbeats = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT PROCESS_NAME, PROCESS_BASE, NODE, INSTANCE, STATUS, PROGRESS,
                            TIMESTAMPDIFF(MICROSECOND, HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM PROCESS_HEARTBEAT
                    WHERE STATUS = 'RUNNING'
                     """)) {

            var rs = stmt.executeQuery();
            while (rs.next()) {
                int progress = rs.getInt("PROGRESS");
                heartbeats.add(new ProcessHeartbeat(
                        rs.getString("PROCESS_NAME"),
                        rs.getString("PROCESS_BASE"),
                        rs.getInt("NODE"),
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

    public List<ProcessHeartbeat> getProcessHeartbeatsForNode(int node) {
        List<ProcessHeartbeat> heartbeats = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT PROCESS_NAME, PROCESS_BASE, NODE, INSTANCE, STATUS, PROGRESS,
                            TIMESTAMPDIFF(MICROSECOND, HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM PROCESS_HEARTBEAT
                    WHERE NODE=?
                    AND STATUS='RUNNING'
                     """)) {

            stmt.setInt(1, node);

            var rs = stmt.executeQuery();
            while (rs.next()) {
                int progress = rs.getInt("PROGRESS");
                heartbeats.add(new ProcessHeartbeat(
                        rs.getString("PROCESS_NAME"),
                        rs.getString("PROCESS_BASE"),
                        rs.getInt("NODE"),
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

}
