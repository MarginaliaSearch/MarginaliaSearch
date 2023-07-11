package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.model.MessageQueueEntry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MessageQueueViewService {

    private final HikariDataSource dataSource;

    @Inject
    public MessageQueueViewService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<MessageQueueEntry> getLastEntries(int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setInt(1, n);
            List<MessageQueueEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(new MessageQueueEntry(
                        rs.getLong("ID"),
                        rs.getLong("RELATED_ID"),
                        rs.getString("SENDER_INBOX"),
                        rs.getString("RECIPIENT_INBOX"),
                        rs.getString("FUNCTION"),
                        trimUUID(rs.getString("OWNER_INSTANCE")),
                        rs.getLong("OWNER_TICK"),
                        rs.getString("STATE"),
                        rs.getTimestamp("CREATED_TIME").toLocalDateTime().toLocalTime().toString(),
                        rs.getTimestamp("UPDATED_TIME").toLocalDateTime().toLocalTime().toString(),
                        rs.getInt("TTL")
                ));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
    private String trimUUID(String uuid) {
        if (null == uuid) {
            return "";
        }

        if (uuid.length() > 8) {
            return uuid.substring(0, 8);
        }
        return uuid;
    }

}
