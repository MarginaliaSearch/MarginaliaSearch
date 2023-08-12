package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.model.MessageQueueEntry;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import spark.Request;
import spark.Response;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class MessageQueueService {

    private final HikariDataSource dataSource;
    private final MqPersistence persistence;

    @Inject
    public MessageQueueService(HikariDataSource dataSource, MqPersistence persistence) {
        this.dataSource = dataSource;
        this.persistence = persistence;
    }


    public Object viewMessageModel(Request request, Response response) {
        return Map.of("message", getMessage(Long.parseLong(request.params("id"))),
                "relatedMessages", getRelatedMessages(Long.parseLong(request.params("id"))));
    }


    public Object listMessageQueueModel(Request request, Response response) {
        String inboxParam = request.queryParams("inbox");
        String instanceParam = request.queryParams("instance");
        String afterParam = request.queryParams("after");

        long afterId = Optional.ofNullable(afterParam).map(Long::parseLong).orElse(Long.MAX_VALUE);

        List<MessageQueueEntry> entries;

        String mqFilter = "filter=none";
        if (inboxParam != null) {
            mqFilter = "inbox=" + inboxParam;
            entries = getEntriesForInbox(inboxParam, afterId, 20);
        }
        else if (instanceParam != null) {
            mqFilter = "instance=" + instanceParam;
            entries = getEntriesForInstance(instanceParam, afterId, 20);
        }
        else {
            entries = getEntries(afterId, 20);
        }

        Object next;

        if (entries.size() == 20)
            next = entries.stream().mapToLong(MessageQueueEntry::id).min().getAsLong();
        else
            next = "";

        return Map.of("messages", entries,
                "next", next,
                "mqFilter", mqFilter);
    }

    public Object newMessageModel(Request request, Response response) {
        String idParam = request.queryParams("id");
        if (null == idParam)
            return Map.of("relatedId", "-1");

        var message = getMessage(Long.parseLong(idParam));
        if (message != null)
            return message;

        return Map.of("relatedId", "-1");
    }

    public Object replyMessageModel(Request request, Response response) {
        String idParam = request.params("id");

        var message = getMessage(Long.parseLong(idParam));

        return Map.of("relatedId", message.id(),
                "recipientInbox", message.senderInbox(),
                "function", "REPLY");
    }

    public Object createMessage(Request request, Response response) throws Exception {
        String recipient = request.queryParams("recipientInbox");
        String sender = request.queryParams("senderInbox");
        String relatedMessage = request.queryParams("relatedId");
        String function = request.queryParams("function");
        String payload = request.queryParams("payload");

        persistence.sendNewMessage(recipient,
                sender.isBlank() ? null : sender,
                relatedMessage == null ? null : Long.parseLong(relatedMessage),
                function,
                payload,
                null);

        return "";
    }

    public Object viewMessageForEditStateModel(Request request, Response response) throws SQLException {
        return persistence.getMessage(Long.parseLong(request.params("id")));
    }

    public Object editMessageState(Request request, Response response) throws SQLException {
        MqMessageState state = MqMessageState.valueOf(request.queryParams("state"));
        long id = Long.parseLong(request.params("id"));
        persistence.updateMessageState(id, state);
        return "";
    }

    public List<MessageQueueEntry> getLastEntries(int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setInt(1, n);
            List<MessageQueueEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(newEntry(rs));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
    public MessageQueueEntry getMessage(long id) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE ID=?
                """)) {

            query.setLong(1, id);

            var rs = query.executeQuery();
            if (rs.next()) {
                return newEntry(rs);
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return null;
    }

    public Object getLastEntriesForInbox(String inbox, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE RECIPIENT_INBOX=?
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setString(1, inbox);
            query.setInt(2, n);
            List<MessageQueueEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(newEntry(rs));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<MessageQueueEntry> getEntriesForInbox(String inbox, long afterId, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE ID < ? AND (RECIPIENT_INBOX = ? OR SENDER_INBOX = ?)
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setLong(1, afterId);
            query.setString(2, inbox);
            query.setString(3, inbox);
            query.setInt(4, n);

            List<MessageQueueEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(newEntry(rs));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<MessageQueueEntry> getEntriesForInstance(String instance, long afterId, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE ID < ? AND OWNER_INSTANCE = ?
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setLong(1, afterId);
            query.setString(2, instance);
            query.setInt(3, n);

            List<MessageQueueEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(newEntry(rs));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<MessageQueueEntry> getEntries(long afterId, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE ID < ?
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setLong(1, afterId);
            query.setInt(2, n);

            List<MessageQueueEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(newEntry(rs));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<MessageQueueEntry> getRelatedMessages(long relatedId) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                     (SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                     FROM MESSAGE_QUEUE
                     WHERE RELATED_ID = ?
                     ORDER BY ID DESC
                     LIMIT 100)
                     UNION
                     (SELECT ID, RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                     FROM MESSAGE_QUEUE
                     WHERE ID = (SELECT RELATED_ID FROM MESSAGE_QUEUE WHERE ID=?)
                     ORDER BY ID DESC
                     LIMIT 100)
                     """)) {

            query.setLong(1, relatedId);
            query.setLong(2, relatedId);

            List<MessageQueueEntry> entries = new ArrayList<>(100);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(newEntry(rs));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private MessageQueueEntry newEntry(ResultSet rs) throws SQLException {
        return new MessageQueueEntry(
                rs.getLong("ID"),
                rs.getLong("RELATED_ID"),
                rs.getString("SENDER_INBOX"),
                rs.getString("RECIPIENT_INBOX"),
                rs.getString("FUNCTION"),
                rs.getString("PAYLOAD"),
                rs.getString("OWNER_INSTANCE"),
                rs.getLong("OWNER_TICK"),
                rs.getString("STATE"),
                rs.getTimestamp("CREATED_TIME").toLocalDateTime().toLocalTime().toString(),
                rs.getTimestamp("UPDATED_TIME").toLocalDateTime().toLocalTime().toString(),
                rs.getInt("TTL"));
    }
}
