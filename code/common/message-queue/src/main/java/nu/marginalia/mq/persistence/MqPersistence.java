package nu.marginalia.mq.persistence;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.MqMessage;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

@Singleton
public class MqPersistence {
    private final HikariDataSource dataSource;

    @Inject
    public MqPersistence(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Flags messages as dead if they have not been set to a terminal state within a TTL after the last update. */
    public int reapDeadMessages() throws SQLException {
        try (var conn = dataSource.getConnection();
             var setToDead = conn.prepareStatement("""
                     UPDATE MESSAGE_QUEUE
                     SET STATE='DEAD', UPDATED_TIME=CURRENT_TIMESTAMP(6)
                     WHERE STATE IN ('NEW', 'ACK')
                     AND TTL IS NOT NULL
                     AND TIMESTAMPDIFF(SECOND, UPDATED_TIME, CURRENT_TIMESTAMP(6)) > TTL
                     """)) {
            return setToDead.executeUpdate();
        }
    }

    /** Removes messages that have been set to a terminal state a while after their last update timestamp */
    public int cleanOldMessages() throws SQLException {
        try (var conn = dataSource.getConnection();
             var setToDead = conn.prepareStatement("""
                     DELETE FROM MESSAGE_QUEUE
                     WHERE STATE = 'OK'
                     AND TTL IS NOT NULL
                     AND TIMESTAMPDIFF(SECOND, UPDATED_TIME, CURRENT_TIMESTAMP(6)) > 3600
                     """)) {
            return setToDead.executeUpdate();
        }
    }

    /**
     * Adds a new message to the message queue.
     *
     * @param recipientInboxName The recipient's inbox name
     * @param senderInboxName (nullable) The sender's inbox name. Only needed if a reply is expected. If null, the message is not expected to be replied to.
     * @param function The function to call
     * @param payload The payload to send, typically JSON.
     * @param ttl (nullable) The time to live of the message, in seconds. If null, the message will never set to DEAD.
     * @return The id of the message
     */
    public long sendNewMessage(String recipientInboxName,
                               @Nullable
                               String senderInboxName,
                               String function,
                               String payload,
                               @Nullable Duration ttl
                               ) throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO MESSAGE_QUEUE(RECIPIENT_INBOX, SENDER_INBOX, FUNCTION, PAYLOAD, TTL)
                     VALUES(?, ?, ?, ?, ?)
                     """);
             var lastIdQuery = conn.prepareStatement("SELECT LAST_INSERT_ID()")) {

            stmt.setString(1, recipientInboxName);

            if (senderInboxName == null) stmt.setNull(2, java.sql.Types.VARCHAR);
            else stmt.setString(2, senderInboxName);

            stmt.setString(3, function);
            stmt.setString(4, payload);
            if (ttl == null) stmt.setNull(5, java.sql.Types.BIGINT);
            else stmt.setLong(5, ttl.toSeconds());

            stmt.executeUpdate();
            var rsp = lastIdQuery.executeQuery();

            if (!rsp.next()) {
                throw new IllegalStateException("No last insert id");
            }

            return rsp.getLong(1);
        }
    }

    /** Modifies the state of a message by id */
    public void updateMessageState(long id, MqMessageState mqMessageState) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     UPDATE MESSAGE_QUEUE
                     SET STATE=?, UPDATED_TIME=CURRENT_TIMESTAMP(6)
                     WHERE ID=?
                     """)) {
            stmt.setString(1, mqMessageState.name());
            stmt.setLong(2, id);

            if (stmt.executeUpdate() != 1) {
                throw new IllegalArgumentException("No rows updated");
            }
        }
    }

    /** Creates a new message in the queue referencing as a reply to an existing message
     *  This message will have it's RELATED_ID set to the original message's ID.
     */
    public long sendResponse(long id, MqMessageState mqMessageState, String message) throws SQLException {
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (var updateState = conn.prepareStatement("""
                    UPDATE MESSAGE_QUEUE
                    SET STATE=?, UPDATED_TIME=CURRENT_TIMESTAMP(6)
                    WHERE ID=?
                    """);
                 var addResponse = conn.prepareStatement("""
                         INSERT INTO MESSAGE_QUEUE(RECIPIENT_INBOX, RELATED_ID, FUNCTION, PAYLOAD)
                         SELECT SENDER_INBOX, ID, ?, ?
                         FROM MESSAGE_QUEUE
                         WHERE ID=? AND SENDER_INBOX IS NOT NULL
                         """);
                 var lastIdQuery = conn.prepareStatement("SELECT LAST_INSERT_ID()")
            ) {

                updateState.setString(1, mqMessageState.name());
                updateState.setLong(2, id);
                if (updateState.executeUpdate() != 1) {
                    throw new IllegalArgumentException("No rows updated");
                }

                addResponse.setString(1, "REPLY");
                addResponse.setString(2, message);
                addResponse.setLong(3, id);
                if (addResponse.executeUpdate() != 1) {
                    throw new IllegalArgumentException("No rows updated");
                }

                var rsp = lastIdQuery.executeQuery();
                if (!rsp.next()) {
                    throw new IllegalStateException("No last insert id");
                }
                long newId = rsp.getLong(1);

                conn.commit();

                return newId;
            } catch (SQLException|IllegalStateException|IllegalArgumentException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }


    /** Marks unclaimed messages addressed to this inbox with instanceUUID and tick,
     * then returns the number of messages marked.  This is an atomic operation that
     * ensures that messages aren't double processed.
     */
    private int markInboxMessages(String inboxName, String instanceUUID, long tick, int n) throws SQLException {
        try (var conn = dataSource.getConnection();
             var updateStmt = conn.prepareStatement("""
                     UPDATE MESSAGE_QUEUE
                     SET OWNER_INSTANCE=?, OWNER_TICK=?, UPDATED_TIME=CURRENT_TIMESTAMP(6), STATE='ACK'
                     WHERE RECIPIENT_INBOX=?
                     AND OWNER_INSTANCE IS NULL AND STATE='NEW'
                     ORDER BY ID ASC
                     LIMIT ?
                     """);
        ) {
            updateStmt.setString(1, instanceUUID);
            updateStmt.setLong(2, tick);
            updateStmt.setString(3, inboxName);
            updateStmt.setInt(4, n);
            return updateStmt.executeUpdate();
        }
    }

    /**  Marks unclaimed messages addressed to this inbox with instanceUUID and tick,
     * then returns these messages.
     */
    public Collection<MqMessage> pollInbox(String inboxName, String instanceUUID, long tick, int n) throws SQLException {

        // Mark new messages as claimed
        int expected = markInboxMessages(inboxName, instanceUUID, tick, n);
        if (expected == 0) {
            return Collections.emptyList();
        }

        // Then fetch the messages that were marked
        try (var conn = dataSource.getConnection();
             var queryStmt = conn.prepareStatement("""
                     SELECT ID, RELATED_ID, FUNCTION, PAYLOAD, STATE, SENDER_INBOX FROM MESSAGE_QUEUE
                     WHERE OWNER_INSTANCE=? AND OWNER_TICK=?
                     """)
        ) {
            queryStmt.setString(1, instanceUUID);
            queryStmt.setLong(2, tick);
            var rs = queryStmt.executeQuery();

            List<MqMessage> messages = new ArrayList<>(expected);

            while (rs.next()) {
                long msgId = rs.getLong(1);
                long relatedId = rs.getLong(2);

                String function = rs.getString(3);
                String payload = rs.getString(4);

                MqMessageState state = MqMessageState.valueOf(rs.getString(5));
                boolean expectsResponse = rs.getBoolean(6);

                var msg = new MqMessage(msgId, relatedId, function, payload, state, expectsResponse);

                messages.add(msg);
            }

            return messages;
        }

    }


    /**  Marks unclaimed messages addressed to this inbox with instanceUUID and tick,
     * then returns these messages.
     */
    public Collection<MqMessage> pollReplyInbox(String inboxName, String instanceUUID, long tick, int n) throws SQLException {

        // Mark new messages as claimed
        int expected = markInboxMessages(inboxName, instanceUUID, tick, n);
        if (expected == 0) {
            return Collections.emptyList();
        }

        // Then fetch the messages that were marked
        try (var conn = dataSource.getConnection();
             var queryStmt = conn.prepareStatement("""
                     SELECT SELF.ID, SELF.RELATED_ID, SELF.FUNCTION, SELF.PAYLOAD, PARENT.STATE FROM MESSAGE_QUEUE SELF
                     LEFT JOIN MESSAGE_QUEUE PARENT ON SELF.RELATED_ID=PARENT.ID
                     WHERE SELF.OWNER_INSTANCE=? AND SELF.OWNER_TICK=?
                     """)
        ) {
            queryStmt.setString(1, instanceUUID);
            queryStmt.setLong(2, tick);
            var rs = queryStmt.executeQuery();

            List<MqMessage> messages = new ArrayList<>(expected);

            while (rs.next()) {
                long msgId = rs.getLong(1);
                long relatedId = rs.getLong(2);

                String function = rs.getString(3);
                String payload = rs.getString(4);

                MqMessageState state = MqMessageState.valueOf(rs.getString(5));

                var msg = new MqMessage(msgId, relatedId, function, payload, state, false);

                messages.add(msg);
            }

            return messages;
        }
    }

    /** Returns the last N messages sent to this inbox */
    public List<MqMessage> lastNMessages(String inboxName, int lastN) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT ID, RELATED_ID, FUNCTION, PAYLOAD, STATE, SENDER_INBOX FROM MESSAGE_QUEUE
                     WHERE RECIPIENT_INBOX = ?
                     ORDER BY ID DESC LIMIT ?
                     """)) {

            stmt.setString(1, inboxName);
            stmt.setInt(2, lastN);
            List<MqMessage> messages = new ArrayList<>(lastN);

            var rs = stmt.executeQuery();
            while (rs.next()) {
                long msgId = rs.getLong(1);
                long relatedId = rs.getLong(2);

                String function = rs.getString(3);
                String payload = rs.getString(4);

                MqMessageState state = MqMessageState.valueOf(rs.getString(5));
                boolean expectsResponse = rs.getBoolean(6);

                var msg = new MqMessage(msgId, relatedId, function, payload, state, expectsResponse);

                messages.add(msg);
            }

            Lists.reverse(messages);
            return messages;
        }

    }
}
