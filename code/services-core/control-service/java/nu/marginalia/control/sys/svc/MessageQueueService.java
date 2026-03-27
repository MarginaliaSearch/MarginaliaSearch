package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.sys.model.MessageQueueEntry;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Singleton
public class MessageQueueService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;
    private final MqPersistence persistence;

    private ControlRendererFactory.Renderer messageQueueRenderer;
    private ControlRendererFactory.Renderer updateMessageStateRenderer;
    private ControlRendererFactory.Renderer newMessageRenderer;
    private ControlRendererFactory.Renderer viewMessageRenderer;

    @Inject
    public MessageQueueService(HikariDataSource dataSource,
                               ControlRendererFactory rendererFactory,
                               MqPersistence persistence) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
        this.persistence = persistence;


    }

    public void register(Jooby jooby) throws IOException {
        this.messageQueueRenderer = rendererFactory.renderer("control/sys/message-queue");
        this.updateMessageStateRenderer = rendererFactory.renderer("control/sys/update-message-state");
        this.newMessageRenderer = rendererFactory.renderer("control/sys/new-message");
        this.viewMessageRenderer = rendererFactory.renderer("control/sys/view-message");

        jooby.get("/message-queue", this::listMessageQueueModel);
        jooby.post("/message-queue/", this::createMessage);
        jooby.get("/message-queue/new", this::newMessageModel);
        jooby.get("/message-queue/{id}", this::viewMessageModel);
        jooby.get("/message-queue/{id}/reply", this::replyMessageModel);
        jooby.get("/message-queue/{id}/edit", this::viewMessageForEditStateModel);
        jooby.post("/message-queue/{id}/edit", this::editMessageState);
    }

    private Object listMessageQueueModel(Context ctx) throws Exception {
        String inboxParam = ctx.query("inbox").valueOrNull();
        String instanceParam = ctx.query("instance").valueOrNull();
        String afterParam = ctx.query("after").valueOrNull();

        long afterId = Optional.ofNullable(afterParam).map(Long::parseLong).orElse(Long.MAX_VALUE);

        List<MessageQueueEntry> entries;

        String mqFilter = "filter=none";
        if (inboxParam != null && !inboxParam.isBlank()) {
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

        List<String> inboxes = getAllInboxes();

        ctx.setResponseType(MediaType.html);
        return messageQueueRenderer.render(Map.of("messages", entries,
                "next", next,
                "filterInbox", Objects.requireNonNullElse(inboxParam, ""),
                "inboxes", inboxes,
                "mqFilter", mqFilter));
    }

    private Object createMessage(Context ctx) throws Exception {
        String recipient = ctx.form("recipientInbox").valueOrNull();
        String sender = ctx.form("senderInbox").valueOrNull();
        String relatedMessage = ctx.form("relatedId").valueOrNull();
        String function = ctx.form("function").valueOrNull();
        String payload = ctx.form("payload").valueOrNull();

        persistence.sendNewMessage(recipient,
                sender == null || sender.isBlank() ? null : sender,
                relatedMessage == null ? null : Long.parseLong(relatedMessage),
                function,
                payload,
                null);

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToMessageQueue.render(null);
    }

    private Object newMessageModel(Context ctx) throws Exception {
        String idParam = ctx.query("id").valueOrNull();

        Object model;
        if (null == idParam) {
            model = Map.of("relatedId", "-1");
        } else {
            MessageQueueEntry message = getMessage(Long.parseLong(idParam));
            model = (message != null) ? message : Map.of("relatedId", "-1");
        }

        ctx.setResponseType(MediaType.html);
        return newMessageRenderer.render(model);
    }

    private Object viewMessageModel(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.path("id").value());
        MessageQueueEntry message = getMessage(id);
        List<MessageQueueEntry> relatedMessages = getRelatedMessages(id);

        // Use HashMap instead of Map.of to permit null values (message may be null for unknown IDs)
        HashMap<String, Object> model = new HashMap<>();
        model.put("message", message);
        model.put("relatedMessages", relatedMessages);

        ctx.setResponseType(MediaType.html);
        return viewMessageRenderer.render(model);
    }

    private Object replyMessageModel(Context ctx) throws Exception {
        String idParam = ctx.path("id").value();
        MessageQueueEntry message = getMessage(Long.parseLong(idParam));

        ctx.setResponseType(MediaType.html);
        return newMessageRenderer.render(Map.of("relatedId", message.id(),
                "recipientInbox", message.senderInbox(),
                "function", "REPLY"));
    }

    private Object viewMessageForEditStateModel(Context ctx) throws Exception {
        ctx.setResponseType(MediaType.html);
        return updateMessageStateRenderer.render(
                persistence.getMessage(Long.parseLong(ctx.path("id").value())));
    }

    private Object editMessageState(Context ctx) throws Exception {
        MqMessageState state = MqMessageState.valueOf(ctx.form("state").valueOrNull());
        long id = Long.parseLong(ctx.path("id").value());
        persistence.updateMessageState(id, state);

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToMessageQueue.render(null);
    }

    private List<String> getAllInboxes() throws SQLException {
        List<String> inboxes = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement query = conn.prepareStatement("""
                SELECT DISTINCT RECIPIENT_INBOX
                FROM MESSAGE_QUEUE
                WHERE RECIPIENT_INBOX IS NOT NULL
                """))
        {
            ResultSet rs = query.executeQuery();
            while (rs.next()) {
                inboxes.add(rs.getString(1));
            }
        }

        // Remove transient inboxes
        inboxes.removeIf(inbox -> inbox.contains("//"));

        // Sort inboxes so that fsm inboxes are last
        Comparator<String> comparator = (o1, o2) -> {
            int diff = Boolean.compare(o1.startsWith("fsm:"), o2.startsWith("fsm:"));
            if (diff != 0)
                return diff;

            return o1.compareTo(o2);
        };

        inboxes.sort(comparator);

        return inboxes;
    }

    public List<MessageQueueEntry> getLastEntries(int n) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, AUDIT_RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setInt(1, n);
            List<MessageQueueEntry> entries = new ArrayList<>(n);
            ResultSet rs = query.executeQuery();
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, AUDIT_RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE ID=?
                """)) {

            query.setLong(1, id);

            ResultSet rs = query.executeQuery();
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, AUDIT_RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE RECIPIENT_INBOX=?
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setString(1, inbox);
            query.setInt(2, n);
            List<MessageQueueEntry> entries = new ArrayList<>(n);
            ResultSet rs = query.executeQuery();
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, AUDIT_RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
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
            ResultSet rs = query.executeQuery();
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, AUDIT_RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE ID < ? AND OWNER_INSTANCE = ?
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setLong(1, afterId);
            query.setString(2, instance);
            query.setInt(3, n);

            List<MessageQueueEntry> entries = new ArrayList<>(n);
            ResultSet rs = query.executeQuery();
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement query = conn.prepareStatement("""
                SELECT ID, RELATED_ID, AUDIT_RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                FROM MESSAGE_QUEUE
                WHERE ID < ?
                ORDER BY ID DESC
                LIMIT ?
                """)) {

            query.setLong(1, afterId);
            query.setInt(2, n);

            List<MessageQueueEntry> entries = new ArrayList<>(n);
            ResultSet rs = query.executeQuery();
            while (rs.next()) {
                entries.add(newEntry(rs));
            }
            return entries;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Get all messages related to a given message, including the given message itself */
    public List<MessageQueueEntry> getRelatedMessages(long relatedId) {
        NavigableSet<Long> newRelatedIds = new TreeSet<>();
        Set<Long> addedIds = new HashSet<>();
        Set<Long> queriedIds = new HashSet<>();
        newRelatedIds.add(relatedId);

        List<MessageQueueEntry> entries = new ArrayList<>();

        // This is not a very performant way of doing this, but it's not a very common operation either
        // and only available within the operator user interface.
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT ID, RELATED_ID, AUDIT_RELATED_ID, SENDER_INBOX, RECIPIENT_INBOX, FUNCTION, PAYLOAD, OWNER_INSTANCE, OWNER_TICK, STATE, CREATED_TIME, UPDATED_TIME, TTL
                     FROM MESSAGE_QUEUE
                     WHERE (ID = ? OR RELATED_ID = ? OR AUDIT_RELATED_ID = ?)
                     AND STATE != 'DEAD' AND FUNCTION != 'MONITOR'
                     ORDER BY ID DESC
                     """)) {

            while (!newRelatedIds.isEmpty()) {
                Long nextId = newRelatedIds.pollFirst();

                if (nextId == null || !queriedIds.add(nextId))
                    continue;

                ps.setLong(1, nextId);
                ps.setLong(2, nextId);
                ps.setLong(3, nextId);

                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    MessageQueueEntry entry = newEntry(rs);

                    if (addedIds.add(entry.id()))
                        entries.add(entry);

                    if (!queriedIds.contains(entry.id()))
                        newRelatedIds.add(entry.id());
                    if (entry.hasRelatedMessage() && !queriedIds.contains(entry.relatedId()))
                        newRelatedIds.add(entry.relatedId());
                    if (entry.hasAuditRelation() && !queriedIds.contains(entry.auditRelatedId()))
                        newRelatedIds.add(entry.auditRelatedId());
                }
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        entries.sort(Comparator.comparingLong(MessageQueueEntry::id).reversed());
        return entries;
    }

    private MessageQueueEntry newEntry(ResultSet rs) throws SQLException {
        return new MessageQueueEntry(
                rs.getLong("ID"),
                rs.getLong("RELATED_ID"),
                rs.getLong("AUDIT_RELATED_ID"),
                rs.getString("SENDER_INBOX"),
                rs.getString("RECIPIENT_INBOX"),
                rs.getString("FUNCTION"),
                rs.getString("PAYLOAD"),
                rs.getString("OWNER_INSTANCE"),
                rs.getLong("OWNER_TICK"),
                rs.getString("STATE"),
                rs.getTimestamp("CREATED_TIME").toLocalDateTime().toString(),
                rs.getTimestamp("UPDATED_TIME").toLocalDateTime().toString(),
                rs.getInt("TTL"));
    }
}
