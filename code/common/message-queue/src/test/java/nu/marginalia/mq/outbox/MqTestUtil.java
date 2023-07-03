package nu.marginalia.mq.outbox;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MqMessageState;
import org.junit.jupiter.api.Assertions;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MqTestUtil {
    public static List<MqMessageRow> getMessages(HikariDataSource dataSource, String inbox) {
        List<MqMessageRow> messages = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT ID, RELATED_ID,
                        SENDER_INBOX, RECIPIENT_INBOX,
                        FUNCTION, PAYLOAD,
                        STATE,
                        OWNER_INSTANCE, OWNER_TICK,
                        CREATED_TIME, UPDATED_TIME,
                        TTL
                FROM PROC_MESSAGE
                WHERE RECIPIENT_INBOX = ?
                 """))
        {
            stmt.setString(1, inbox);
            var rsp = stmt.executeQuery();
            while (rsp.next()) {
                messages.add(new MqMessageRow(
                        rsp.getLong("ID"),
                        rsp.getLong("RELATED_ID"),
                        rsp.getString("SENDER_INBOX"),
                        rsp.getString("RECIPIENT_INBOX"),
                        rsp.getString("FUNCTION"),
                        rsp.getString("PAYLOAD"),
                        MqMessageState.valueOf(rsp.getString("STATE")),
                        rsp.getString("OWNER_INSTANCE"),
                        rsp.getLong("OWNER_TICK"),
                        rsp.getTimestamp("CREATED_TIME").getTime(),
                        rsp.getTimestamp("UPDATED_TIME").getTime(),
                        rsp.getLong("TTL")
                ));
            }
        }
        catch (SQLException ex) {
            Assertions.fail(ex);
        }
        return messages;
    }
}
