package nu.marginalia.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.model.EventLogEntry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class EventLogService {

    private final HikariDataSource dataSource;

    @Inject
    public EventLogService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<EventLogEntry> getLastEntries(int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                 SELECT SERVICE_NAME, INSTANCE, EVENT_TIME, EVENT_TYPE, EVENT_MESSAGE
                 FROM PROC_SERVICE_EVENTLOG ORDER BY ID DESC LIMIT ?
                 """)) {

            query.setInt(1, n);
            List<EventLogEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(new EventLogEntry(
                        rs.getString("SERVICE_NAME"),
                        trimUUID(rs.getString("INSTANCE")),
                        rs.getTimestamp("EVENT_TIME").toLocalDateTime().toLocalTime().toString(),
                        rs.getString("EVENT_TYPE"),
                        rs.getString("EVENT_MESSAGE")
                ));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
    private String trimUUID(String uuid) {
        if (uuid.length() > 8) {
            return uuid.substring(0, 8);
        }
        return uuid;
    }

}
