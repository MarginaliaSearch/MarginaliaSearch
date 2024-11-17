package nu.marginalia.status.db;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.status.StatusMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class StatusMetricDb {
    private final Connection connection;
    private static final Logger logger = LoggerFactory.getLogger(StatusMetricDb.class);

    private final ConcurrentHashMap<String, Integer> metricIds = new ConcurrentHashMap<>();

    @Inject
    public StatusMetricDb(
            @Named("statusDbPath") String statusDbPath
    ) throws SQLException {
        String connectionUrl = "jdbc:sqlite:" + statusDbPath;

        connection = DriverManager.getConnection(connectionUrl);
        connection.setAutoCommit(true);

        try (var stmt = connection.createStatement()) {
            stmt.execute(
                    """
                        CREATE TABLE IF NOT EXISTS metrics (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE
                        );
                        """);
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS measurements (
                            metricId INTEGER,
                            timestamp DATETIME NOT NULL,
                            result BOOLEAN NOT NULL,
                            request_time_ms INTEGER,
                            FOREIGN KEY (metricId) REFERENCES metrics(id)
                        )
                        """
            );
        }
    }

    public void pruneOldResults() {
        try (var stmt = connection.prepareStatement(
                """
                    DELETE FROM measurements
                    WHERE datetime(timestamp/1000, 'unixepoch') < datetime('now', '-14 days')
                    """
        )) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to prune old results", e);
        }
    }

    private int getOrAssignMetricId(String name) {
        return metricIds.computeIfAbsent(name, k -> {
            try (var insertStmt = connection.prepareStatement(
                    """
                        INSERT OR IGNORE INTO metrics (name)
                        VALUES (?)
                        """);
                 var queryStmt = connection.prepareStatement(
                         """
                             SELECT id
                             FROM metrics
                             WHERE name = ?
                             """
                 )
            ) {
                insertStmt.setString(1, name);
                insertStmt.executeUpdate();

                queryStmt.setString(1, name);
                var rs = queryStmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException("Failed to get or assign metric id");
        });
    }

    public void saveResult(StatusMetric.MeasurementResult result) throws SQLException {
        try (var stmt = connection.prepareStatement(
                """
                    INSERT INTO measurements (metricId, timestamp, result, request_time_ms)
                    VALUES (?, ?, ?, ?)
                    """
        )) {
            stmt.setInt(1, getOrAssignMetricId(result.name()));
            stmt.setDate(2, new Date(result.when().toEpochMilli()));
            if (result instanceof StatusMetric.MeasurementResult.Success) {
                stmt.setBoolean(3, true);
                stmt.setObject(4, ((StatusMetric.MeasurementResult.Success) result).callDuration().toMillis());
            } else {
                stmt.setBoolean(3, false);
                stmt.setObject(4, null);
            }

            stmt.executeUpdate();
        }
    }

    public boolean isOnline(String name) {
        try (var stmt = connection.prepareStatement(
                """
                    SELECT result
                    FROM measurements
                    WHERE metricId = ?
                    ORDER BY timestamp DESC
                    LIMIT 1
                    """
        )) {
            stmt.setInt(1, getOrAssignMetricId(name));
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("result");
            }
        } catch (SQLException e) {
            logger.error("Failed to check status metric: " + name, e);
        }

        return false;
    }

    public List<MeasurementStatistics> getAllStatistics() throws SQLException {
        List<MeasurementStatistics> statistics = new ArrayList<>();

        for (String name : metricIds.keySet()) {
            MeasurementStatistics stat = getStatistics(name);
            if (stat != null) {
                statistics.add(stat);
            }
        }

        statistics.sort(Comparator.comparing(MeasurementStatistics::name));

        return statistics;
    }

    @Nullable
    public MeasurementStatistics getStatistics(String name) throws SQLException {
        int id = getOrAssignMetricId(name);

        // Let the database do the heavy lifting in aggregating and calculating statistics;
        // we expect of order 10k rows per metric, which is not a lot so this should be relatively fast
        try (var stmt = connection.prepareStatement(
                """
                    SELECT
                        (SELECT result FROM measurements WHERE metricId = ? ORDER BY timestamp DESC LIMIT 1) as latest_result,
                        (SELECT request_time_ms FROM measurements WHERE metricId = ? ORDER BY timestamp DESC LIMIT 1) as latest_request_time,
                        (SELECT timestamp FROM measurements WHERE metricId = ? ORDER BY timestamp DESC LIMIT 1) as latest_timestamp,
                        (SELECT COUNT(*) FROM measurements WHERE metricId = ? AND result = 1) as online_count,
                        (SELECT COUNT(*) FROM measurements WHERE metricId = ?) as total_count,
                        (SELECT SUM(request_time_ms) FROM measurements WHERE metricId = ? AND result = 1) as total_request_time,
                        (SELECT timestamp FROM measurements WHERE metricId = ? AND result = 1 ORDER BY timestamp DESC LIMIT 1) as last_online,
                        (SELECT timestamp FROM measurements WHERE metricId = ? AND result = 0 ORDER BY timestamp DESC LIMIT 1) as last_offline
                    """
        )) {
            // There has got to be a better way to do this

            stmt.setInt(1, id);
            stmt.setInt(2, id);
            stmt.setInt(3, id);
            stmt.setInt(4, id);
            stmt.setInt(5, id);
            stmt.setInt(6, id);
            stmt.setInt(7, id);
            stmt.setInt(8, id);

            var rs = stmt.executeQuery();

            if (rs.next()) {
                boolean isOnline = rs.getBoolean("latest_result");
                long requestTime = rs.getLong("latest_request_time");
                long totalRequestTime = rs.getLong("total_request_time");
                int totalCount = rs.getInt("total_count");
                int onlineCount = rs.getInt("online_count");

                long avgRequestTime;
                double percentOnline;

                if (onlineCount > 0) {
                    avgRequestTime = totalRequestTime / onlineCount;
                    percentOnline = 100.0 * onlineCount / totalCount;
                } else {
                    avgRequestTime = -1;
                    percentOnline = 0;
                }


                Timestamp lastOfflineTs = rs.getTimestamp("last_offline");
                Instant lastOffline = lastOfflineTs == null ? null : lastOfflineTs.toInstant();

                Timestamp lastOnlineTs = rs.getTimestamp("last_online");
                Instant lastOnline = lastOnlineTs == null ? null : lastOnlineTs.toInstant();

                return new MeasurementStatistics(
                        name,
                        isOnline,
                        requestTime,
                        avgRequestTime,
                        percentOnline,
                        (totalCount - onlineCount),
                        onlineCount,
                        lastOffline,
                        lastOnline
                );
            }
        }

        return null;
    }

    public record MeasurementStatistics(
            String name,
            boolean isOnline,
            long requestTimeMs,
            long avgRequestTimeMs,
            double percentOnline,
            int numFailures,
            int numSuccesses,
            @Nullable Instant lastOffline,
            @Nullable Instant lastOnline
    ) {
        public String getTimeSinceLastOnline() {
            if (lastOnline == null) {
                return "-";
            }
            return prettyPrintDuration(Duration.between(lastOnline, Instant.now())) + " ago";
        }
        public String getTimeSinceLastOffline() {
            if (lastOffline == null) {
                return "-";
            }
            return prettyPrintDuration(Duration.between(lastOffline, Instant.now())) + " ago";
        }

        private String prettyPrintDuration(Duration duration) {
            if (duration.compareTo(Duration.ofSeconds(60)) < 0) {
                return duration.toSeconds() + "s";
            }

            if (duration.compareTo(Duration.ofMinutes(60)) < 0) {
                long seconds = duration.getSeconds();
                long absSeconds = Math.abs(seconds);
                return String.format(
                        "%dm%02ds",
                        absSeconds / 60,
                        absSeconds % 60
                );
            }

            if (duration.compareTo(Duration.ofHours(24)) < 0) {
                long seconds = duration.getSeconds();
                long absSeconds = Math.abs(seconds);
                return String.format(
                        "%dh%02dm",
                        absSeconds / 3600,
                        (absSeconds % 3600) / 60
                );
            }

            long seconds = duration.getSeconds();
            long absSeconds = Math.abs(seconds);
            return String.format(
                    "%dd%02dh",
                    absSeconds / 86400,
                    (absSeconds % 86400) / 3600
            );
        }
    }

}
