package nu.marginalia.mq.persistence;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

import static nu.marginalia.mq.MqMessageState.NEW;

/** A persistence layer for the message queue.
 *  <p>
 *  All storage operations must be done through this class.
 */
@Singleton
public class MqPersistence {
    private final HikariDataSource dataSource;
    private final Gson gson;

    private static final Logger logger = LoggerFactory.getLogger(MqPersistence.class);

    public MqPersistence(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.gson = null;
    }
    @Inject
    public MqPersistence(HikariDataSource dataSource, Gson gson) {
        this.dataSource = dataSource;
        this.gson = gson;
    }

    /**
     * Adds a new message to the message queue.
     *
     * @param recipientInboxName The recipient's inbox name
     * @param senderInboxName    (nullable) The sender's inbox name. Only needed if a reply is expected. If null, the message is not expected to be replied to.
     * @param relatedMessageId   (nullable) The id of the message this message is related to. If null, the message is not related to any other message.
     * @param function           The function to call
     * @param payload            The payload to send, typically JSON.
     * @param ttl                (nullable) The time to live of the message, in seconds. If null, the message will never set to DEAD.
     * @return The id of the message
     */
    public long sendNewMessage(String recipientInboxName,
                               @Nullable
                               String senderInboxName,
                               Long relatedMessageId,
                               String function,
                               String payload,
                               @Nullable Duration ttl
                               ) throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO MESSAGE_QUEUE(RECIPIENT_INBOX, SENDER_INBOX, RELATED_ID, AUDIT_RELATED_ID, FUNCTION, PAYLOAD, TTL)
                     VALUES(?, ?, ?, ?, ?, ?, ?)
                     """);
             var lastIdQuery = conn.prepareStatement("SELECT LAST_INSERT_ID()")) {

            stmt.setString(1, recipientInboxName);

            if (senderInboxName == null) stmt.setNull(2, java.sql.Types.VARCHAR);
            else stmt.setString(2, senderInboxName);

            // Translate null to -1, as 0 is a valid id
            stmt.setLong(3, Objects.requireNonNullElse(relatedMessageId, -1L));
            stmt.setLong(4, MqMessageHandlerRegistry.getOriginMessage());

            stmt.setString(5, function);
            stmt.setString(6, payload);
            if (ttl == null) stmt.setNull(7, java.sql.Types.BIGINT);
            else stmt.setLong(7, ttl.toSeconds());

            stmt.executeUpdate();

            if (!conn.getAutoCommit())
                conn.commit();

            var rsp = lastIdQuery.executeQuery();

            if (!rsp.next()) {
                throw new IllegalStateException("No last insert id");
            }

            return rsp.getLong(1);
        }
    }

    /** Modifies the state of a message by id.
     * <p>
     * If the state is 'NEW', ownership information will be stripped to avoid creating
     * a broken message that can't be dequeued because it has an owner.
     *
     * @param id The id of the message
     * @param mqMessageState The new state
     * */
    public void updateMessageState(long id, MqMessageState mqMessageState) throws SQLException {
        if (NEW == mqMessageState) {
            reinitializeMessage(id);
            return;
        }

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

            if (!conn.getAutoCommit())
                conn.commit();
        }
    }

    /** Sets the message to 'NEW' state and removes any owner */
    private void reinitializeMessage(long id) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     UPDATE MESSAGE_QUEUE
                     SET STATE='NEW',
                         OWNER_INSTANCE=NULL,
                         OWNER_TICK=NULL,
                         UPDATED_TIME=CURRENT_TIMESTAMP(6)
                     WHERE ID=?
                     """)) {

            stmt.setLong(1, id);

            if (stmt.executeUpdate() != 1) {
                throw new IllegalArgumentException("No rows updated");
            }

            if (!conn.getAutoCommit())
                conn.commit();
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

    /** Sends an error response to the message with the given id, this is a convenience wrapper for
     * sendResponse() that send a generic error message.  The message will be marked as 'ERR'.
     * <p></p>
     * If an exception is thrown while sending the response, it will be logged, but not rethrown
     * to avoid creating exception handling pyramids.  At this point, we've already given it a college try.
     * */
    public void sendErrorResponse(long msgId, String message, Throwable e) {
        try {
            sendResponse(msgId, MqMessageState.ERR, message + ": " + e.getMessage());
        } catch (SQLException ex) {
            logger.error("Failed to send error response", ex);
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
                     """)
        ) {
            updateStmt.setString(1, instanceUUID);
            updateStmt.setLong(2, tick);
            updateStmt.setString(3, inboxName);
            updateStmt.setInt(4, n);
            var ret = updateStmt.executeUpdate();
            if (!conn.getAutoCommit())
                conn.commit();
            return ret;
        }
    }

    /** Return up to n unprocessed messages from the specified inbox that are in states 'NEW' or 'ACK'
     * without updating their ownership information
     */
    public SequencedCollection<MqMessage> eavesdrop(String inboxName, int n) throws SQLException {
        try (var conn = dataSource.getConnection();
             var queryStmt = conn.prepareStatement("""
                     SELECT
                        ID,
                        RELATED_ID,
                        FUNCTION,
                        PAYLOAD,
                        STATE,
                        SENDER_INBOX IS NOT NULL AS EXPECTS_RESPONSE
                     FROM MESSAGE_QUEUE
                     WHERE STATE IN ('NEW', 'ACK')
                     AND RECIPIENT_INBOX=?
                     LIMIT ?
                     """)
        )
        {
        queryStmt.setString(1, inboxName);
        queryStmt.setInt(2, n);
        var rs = queryStmt.executeQuery();

        List<MqMessage> messages = new ArrayList<>(n);

        while (rs.next()) {
            long msgId = rs.getLong("ID");
            long relatedId = rs.getLong("RELATED_ID");

            String function = rs.getString("FUNCTION");
            String payload = rs.getString("PAYLOAD");

            MqMessageState state = MqMessageState.valueOf(rs.getString("STATE"));
            boolean expectsResponse = rs.getBoolean("EXPECTS_RESPONSE");

            var msg = new MqMessage(msgId, relatedId, function, payload, state, expectsResponse);

            messages.add(msg);
        }

        return messages;
        }

    }

    /** Returns the message with the specified ID
     *
     * @throws SQLException if there is a problem with the database
     * @throws IllegalArgumentException if the message doesn't exist
     */
    public MqMessage getMessage(long id) throws SQLException {
        try (var conn = dataSource.getConnection();
             var queryStmt = conn.prepareStatement("""
                     SELECT
                        ID,
                        RELATED_ID,
                        FUNCTION,
                        PAYLOAD,
                        STATE,
                        SENDER_INBOX IS NOT NULL AS EXPECTS_RESPONSE
                     FROM MESSAGE_QUEUE
                     WHERE ID=?
                     """)
        )
        {
            queryStmt.setLong(1, id);
            var rs = queryStmt.executeQuery();

            if (rs.next()) {
                long msgId = rs.getLong("ID");
                long relatedId = rs.getLong("RELATED_ID");

                String function = rs.getString("FUNCTION");
                String payload = rs.getString("PAYLOAD");

                MqMessageState state = MqMessageState.valueOf(rs.getString("STATE"));
                boolean expectsResponse = rs.getBoolean("EXPECTS_RESPONSE");

                return new MqMessage(msgId, relatedId, function, payload, state, expectsResponse);
            }
        }

        throw new IllegalArgumentException("No message with id " + id);
    }
    /**  Marks unclaimed messages addressed to this inbox with instanceUUID and tick,
     * then returns these messages.
     */
    public Collection<MqMessage> pollInbox(String inboxName, String instanceUUID, long tick, int n) throws SQLException {

        if (dataSource.isClosed()) {
            return Collections.emptyList();
        }

        // Mark new messages as claimed
        int expected = markInboxMessages(inboxName, instanceUUID, tick, n);
        if (expected == 0) {
            return Collections.emptyList();
        }

        // Then fetch the messages that were marked
        try (var conn = dataSource.getConnection();
             var queryStmt = conn.prepareStatement("""
                     SELECT
                        ID,
                        RELATED_ID,
                        FUNCTION,
                        PAYLOAD,
                        STATE,
                        SENDER_INBOX IS NOT NULL AS EXPECTS_RESPONSE
                     FROM MESSAGE_QUEUE
                     WHERE OWNER_INSTANCE=? AND OWNER_TICK=?
                     """)
        ) {
            queryStmt.setString(1, instanceUUID);
            queryStmt.setLong(2, tick);
            var rs = queryStmt.executeQuery();

            List<MqMessage> messages = new ArrayList<>(expected);

            while (rs.next()) {
                long msgId = rs.getLong("ID");
                long relatedId = rs.getLong("RELATED_ID");

                String function = rs.getString("FUNCTION");
                String payload = rs.getString("PAYLOAD");

                MqMessageState state = MqMessageState.valueOf(rs.getString("STATE"));
                boolean expectsResponse = rs.getBoolean("EXPECTS_RESPONSE");

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

        if (dataSource.isClosed()) {
            return Collections.emptyList();
        }

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

            // We want the last N messages in ascending order
            return Lists.reverse(messages);
        }

    }

    /** Modify the message indicated by id to have the given owner information */
    public void changeOwner(long id, String instanceUUID, int tick) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     UPDATE MESSAGE_QUEUE SET OWNER_INSTANCE=?, OWNER_TICK=?
                     WHERE ID=?
                     """)) {
            stmt.setString(1, instanceUUID);
            stmt.setInt(2, tick);
            stmt.setLong(3, id);
            stmt.executeUpdate();

            if (!conn.getAutoCommit())
                conn.commit();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
            int ret = setToDead.executeUpdate();
            if (!conn.getAutoCommit())
                conn.commit();
            return ret;
        }
    }

    /** Removes messages that have been set to a terminal state a while after their last update timestamp */
    public int cleanOldMessages() throws SQLException {
        try (var conn = dataSource.getConnection();
             // Keep 72 hours of messages
             var setToDead = conn.prepareStatement("""
                     DELETE FROM MESSAGE_QUEUE
                     WHERE STATE IN ('OK', 'DEAD', 'NEW')
                     AND (TTL IS NULL OR TTL = 0)
                     AND TIMESTAMPDIFF(SECOND, UPDATED_TIME, CURRENT_TIMESTAMP(6)) > 72*3600
                     """)) {
            int ret = setToDead.executeUpdate();
            if (!conn.getAutoCommit())
                conn.commit();
            return ret;
        }
    }

    public Gson getGson() {
        return gson;
    }

    /** Returns the last message sent to this inbox with a state of 'OK'
     * with an id greater than or equal to fromMsgId
     */
    public Optional<MqMessage> getHeadMessage(String inboxName, long fromMsgId) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                     SELECT ID, RELATED_ID, FUNCTION, PAYLOAD, STATE, SENDER_INBOX 
                     FROM MESSAGE_QUEUE
                     WHERE RECIPIENT_INBOX = ? AND STATE='OK' AND ID >= ?
                     ORDER BY ID DESC LIMIT 1
            """))
        {
            query.setString(1, inboxName);
            query.setLong(2, fromMsgId);

            var rs = query.executeQuery();
            if (rs.next()) {
                long msgId = rs.getLong(1);
                long relatedId = rs.getLong(2);

                String function = rs.getString(3);
                String payload = rs.getString(4);

                MqMessageState state = MqMessageState.valueOf(rs.getString(5));
                boolean expectsResponse = rs.getBoolean(6);

                return Optional.of(new MqMessage(msgId, relatedId, function, payload, state, expectsResponse));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }
}
