package nu.marginalia.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
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

    public List<ServiceHeartbeat> getHeartbeats() {
        List<ServiceHeartbeat> heartbeats = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT SERVICE_NAME, SERVICE_BASE, INSTANCE, ALIVE,
                            TIMESTAMPDIFF(MICROSECOND, HEARTBEAT_TIME, CURRENT_TIMESTAMP(6)) AS TSDIFF
                    FROM PROC_SERVICE_HEARTBEAT
                     """)) {

            var rs = stmt.executeQuery();
            while (rs.next()) {
                heartbeats.add(new ServiceHeartbeat(
                        rs.getString("SERVICE_NAME"),
                        rs.getString("SERVICE_BASE"),
                        rs.getString("INSTANCE"),
                        rs.getInt("TSDIFF") / 1000.,
                        rs.getBoolean("ALIVE")
                ));
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return heartbeats;
    }
}
