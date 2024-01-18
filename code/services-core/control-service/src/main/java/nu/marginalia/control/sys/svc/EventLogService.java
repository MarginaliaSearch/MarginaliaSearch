package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.sys.model.EventLogEntry;
import nu.marginalia.control.sys.model.EventLogServiceFilter;
import nu.marginalia.control.sys.model.EventLogTypeFilter;
import org.apache.logging.log4j.util.Strings;
import spark.Request;
import spark.Response;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

@Singleton
public class EventLogService {

    private final HikariDataSource dataSource;

    @Inject
    public EventLogService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Object eventsListModel(Request request, Response response) {

        String serviceParam = request.queryParams("service");
        String typeParam = request.queryParams("type");
        String afterParam = request.queryParams("after");

        if (Strings.isBlank(serviceParam)) serviceParam = null;
        if (Strings.isBlank(typeParam)) typeParam = null;
        if (Strings.isBlank(afterParam)) afterParam = null;

        long afterId = Optional.ofNullable(afterParam).map(Long::parseLong).orElse(Long.MAX_VALUE);

        List<EventLogTypeFilter> typeFilterList = new ArrayList<>();
        List<String> typenames = getTypeNames();
        for (String typename : typenames) {
            typeFilterList.add(new EventLogTypeFilter(typename, typename,
                    typename.equalsIgnoreCase(typeParam)));
        }

        List<EventLogServiceFilter> serviceFilterList = new ArrayList<>();
        List<String> serviceNames = getServiceNames();
        for (String serviceName : serviceNames) {
            serviceFilterList.add(new EventLogServiceFilter(serviceName, serviceName,
                    serviceName.equalsIgnoreCase(serviceParam)));
        }

        List<EventLogEntry> entries;

        String elFilter = "filter=none";
        if (serviceParam != null && typeParam != null) {
            elFilter = "service=" + serviceParam + "&type=" + typeParam;
            entries = getLastEntriesForTypeAndService(typeParam, serviceParam, afterId, 20);
        }
        else if (serviceParam != null) {
            elFilter = "service=" + serviceParam;
            entries = getLastEntriesForService(serviceParam, afterId, 20);
        }
        else if (typeParam != null) {
            elFilter = "type=" + typeParam;
            entries = getLastEntriesForType(typeParam, afterId, 20);
        }
        else {
            entries = getLastEntries(afterId, 20);
        }

        Object next;
        if (entries.size() == 20)
            next = entries.stream().mapToLong(EventLogEntry::id).min().getAsLong();
        else
            next = "";

        return Map.of(
                "events", entries,
                "types", typeFilterList,
                "services", serviceFilterList,
                "serviceParam", Objects.requireNonNullElse(serviceParam, ""),
                "typeParam", Objects.requireNonNullElse(typeParam, ""),
                "afterParam", Objects.requireNonNullElse(afterParam, ""),
                "next", next,
                "elFilter", elFilter);

    }

    public List<EventLogEntry> getLastEntries(long afterId, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                 SELECT ID, SERVICE_NAME, INSTANCE, EVENT_TIME, EVENT_TYPE, EVENT_MESSAGE
                 FROM SERVICE_EVENTLOG
                 WHERE ID < ?
                 ORDER BY ID DESC
                 LIMIT ?
                 """)) {

            query.setLong(1, afterId);
            query.setInt(2, n);

            List<EventLogEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(new EventLogEntry(
                        rs.getLong("ID"),
                        rs.getString("SERVICE_NAME"),
                        rs.getString("INSTANCE"),
                        rs.getTimestamp("EVENT_TIME").toLocalDateTime().toString(),
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

    public List<EventLogEntry> getLastEntriesForService(String serviceName, long afterId, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                 SELECT ID, SERVICE_NAME, INSTANCE, EVENT_TIME, EVENT_TYPE, EVENT_MESSAGE
                 FROM SERVICE_EVENTLOG
                 WHERE SERVICE_NAME = ?
                 AND ID < ?
                 ORDER BY ID DESC
                 LIMIT ?
                 """)) {

            query.setString(1, serviceName);
            query.setLong(2, afterId);
            query.setInt(3, n);

            List<EventLogEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(new EventLogEntry(
                        rs.getLong("ID"),
                        rs.getString("SERVICE_NAME"),
                        rs.getString("INSTANCE"),
                        rs.getTimestamp("EVENT_TIME").toLocalDateTime().toString(),
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

    public List<EventLogEntry> getLastEntriesForTypeAndService(String typeName, String serviceName, long afterId, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                 SELECT ID, SERVICE_NAME, INSTANCE, EVENT_TIME, EVENT_TYPE, EVENT_MESSAGE
                 FROM SERVICE_EVENTLOG
                 WHERE SERVICE_NAME = ? AND EVENT_TYPE=?
                 AND ID < ?
                 ORDER BY ID DESC
                 LIMIT ?
                 """)) {

            query.setString(1, serviceName);
            query.setString(2, typeName);
            query.setLong(3, afterId);
            query.setInt(4, n);

            List<EventLogEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(new EventLogEntry(
                        rs.getLong("ID"),
                        rs.getString("SERVICE_NAME"),
                        rs.getString("INSTANCE"),
                        rs.getTimestamp("EVENT_TIME").toLocalDateTime().toString(),
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


    public List<EventLogEntry> getLastEntriesForType(String eventType, long afterId, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                 SELECT ID, SERVICE_NAME, INSTANCE, EVENT_TIME, EVENT_TYPE, EVENT_MESSAGE
                 FROM SERVICE_EVENTLOG
                 WHERE EVENT_TYPE = ?
                 AND ID < ?
                 ORDER BY ID DESC
                 LIMIT ?
                 """)) {

            query.setString(1, eventType);
            query.setLong(2, afterId);
            query.setInt(3, n);

            List<EventLogEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(new EventLogEntry(
                        rs.getLong("ID"),
                        rs.getString("SERVICE_NAME"),
                        rs.getString("INSTANCE"),
                        rs.getTimestamp("EVENT_TIME").toLocalDateTime().toString(),
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

    public List<EventLogEntry> getLastEntriesForInstance(String instance, int n) {
        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                 SELECT ID, SERVICE_NAME, INSTANCE, EVENT_TIME, EVENT_TYPE, EVENT_MESSAGE
                 FROM SERVICE_EVENTLOG
                 WHERE INSTANCE = ?
                 ORDER BY ID DESC
                 LIMIT ?
                 """)) {

            query.setString(1, instance);
            query.setInt(2, n);

            List<EventLogEntry> entries = new ArrayList<>(n);
            var rs = query.executeQuery();
            while (rs.next()) {
                entries.add(new EventLogEntry(
                        rs.getLong("ID"),
                        rs.getString("SERVICE_NAME"),
                        rs.getString("INSTANCE"),
                        rs.getTimestamp("EVENT_TIME").toLocalDateTime().toString(),
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

    public List<String> getTypeNames() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT DISTINCT(EVENT_TYPE) FROM SERVICE_EVENTLOG")) {
            List<String> types = new ArrayList<>();
            var rs = stmt.executeQuery();
            while (rs.next()) {
                types.add(rs.getString(1));
            }
            return types;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getServiceNames() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT DISTINCT(SERVICE_NAME) FROM SERVICE_EVENTLOG")) {
            List<String> types = new ArrayList<>();
            var rs = stmt.executeQuery();
            while (rs.next()) {
                types.add(rs.getString(1));
            }
            return types;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


}
