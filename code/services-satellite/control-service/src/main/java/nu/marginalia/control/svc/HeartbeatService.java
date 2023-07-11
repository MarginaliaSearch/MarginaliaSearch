package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.model.ProcessHeartbeat;
import nu.marginalia.control.model.ServiceHeartbeat;

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

    public List<ProcessHeartbeat> getProcessHeartbeats() {
        List<ProcessHeartbeat> heartbeats = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT PROCESS_NAME, PROCESS_BASE, INSTANCE, STATUS, PROGRESS,
                            TIMESTAMPDIFF(MICROSECOND, HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM PROCESS_HEARTBEAT
                     """)) {

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

}
