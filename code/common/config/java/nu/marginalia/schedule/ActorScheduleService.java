package nu.marginalia.schedule;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.schedule.ActorScheduleRow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class ActorScheduleService {
    private static final Logger logger = LoggerFactory.getLogger(ActorScheduleService.class);

    private final HikariDataSource dataSource;

    private static final Map<String, ActorScheduleRow> DEFAULTS = Map.of(
            "LIVE_CRAWLER", new TriggerSchedule("LIVE_CRAWLER", 0, "Live crawler trigger"),
            "DOMAIN_PING",  new WindowSchedule("DOMAIN_PING", 3, 9, "Domain ping window"),
            "RSS_FEEDS",    new TriggerSchedule("RSS_FEEDS", 12, "RSS feed update trigger"),
            "DOM_SAMPLE",   new WindowSchedule("DOM_SAMPLE", 16, 20, "DOM sample capture window"),
            "SCREENGRAB",   new WindowSchedule("SCREENGRAB", 20, 0, "Screenshot capture window"),
            "MAINTENANCE",  new TriggerSchedule("MAINTENANCE", 2, "Maintenance trigger"),
            "SCRAPE_FEEDS", new IntervalSchedule("SCRAPE_FEEDS", 6, "Feed scraping interval")
    );

    @Inject
    public ActorScheduleService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public WindowSchedule getWindow(ActorScheduleRow.Window schedule) {
        ActorScheduleRow row = getSchedule(schedule.name());
        if (row instanceof WindowSchedule window) {
            return window;
        }
        throw new IllegalStateException("Schedule '" + schedule.name() + "' is not a window schedule");
    }

    public TriggerSchedule getTrigger(ActorScheduleRow.Trigger schedule) {
        ActorScheduleRow row = getSchedule(schedule.name());
        if (row instanceof TriggerSchedule trigger) {
            return trigger;
        }
        throw new IllegalStateException("Schedule '" + schedule.name() + "' is not a trigger schedule");
    }

    public IntervalSchedule getInterval(ActorScheduleRow.Interval schedule) {
        ActorScheduleRow row = getSchedule(schedule.name());
        if (row instanceof IntervalSchedule interval) {
            return interval;
        }
        throw new IllegalStateException("Schedule '" + schedule.name() + "' is not an interval schedule");
    }

    private ActorScheduleRow getSchedule(String scheduleName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT SCHEDULE_NAME, SCHEDULE_TYPE, START_HOURS_UTC, END_HOURS_UTC, INTERVAL_HOURS, DESCRIPTION
                     FROM ACTOR_SCHEDULE WHERE SCHEDULE_NAME = ?
                     """))
        {
            stmt.setString(1, scheduleName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String name = rs.getString("SCHEDULE_NAME");
                String type = rs.getString("SCHEDULE_TYPE");
                String description = rs.getString("DESCRIPTION");

                return switch (type) {
                    case "WINDOW" -> new WindowSchedule(name, rs.getInt("START_HOURS_UTC"),
                            rs.getInt("END_HOURS_UTC"), description);
                    case "TRIGGER" -> new TriggerSchedule(name, rs.getInt("START_HOURS_UTC"), description);
                    case "INTERVAL" -> new IntervalSchedule(name, rs.getInt("INTERVAL_HOURS"), description);
                    default -> throw new SQLException("Unknown schedule type: " + type);
                };
            }
        }
        catch (SQLException ex) {
            logger.warn("Failed to read schedule '{}' from database", scheduleName, ex);
        }

        ActorScheduleRow fallback = DEFAULTS.get(scheduleName);
        if (fallback != null) {
            logger.warn("Using fallback default for schedule '{}'", scheduleName);
            return fallback;
        }

        throw new IllegalArgumentException("Unknown schedule: " + scheduleName);
    }

    public List<ActorScheduleRow> getAllSchedules() {
        List<ActorScheduleRow> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT SCHEDULE_NAME, SCHEDULE_TYPE, START_HOURS_UTC, END_HOURS_UTC, INTERVAL_HOURS, DESCRIPTION
                     FROM ACTOR_SCHEDULE ORDER BY SCHEDULE_NAME
                     """))
        {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("SCHEDULE_NAME");
                String type = rs.getString("SCHEDULE_TYPE");
                String description = rs.getString("DESCRIPTION");

                result.add(switch (type) {
                    case "WINDOW" -> new WindowSchedule(name, rs.getInt("START_HOURS_UTC"),
                            rs.getInt("END_HOURS_UTC"), description);
                    case "TRIGGER" -> new TriggerSchedule(name, rs.getInt("START_HOURS_UTC"), description);
                    case "INTERVAL" -> new IntervalSchedule(name, rs.getInt("INTERVAL_HOURS"), description);
                    default -> throw new SQLException("Unknown schedule type: " + type);
                });
            }
        }
        catch (SQLException ex) {
            logger.warn("Failed to read schedules from database", ex);
        }

        return result;
    }

    public void updateWindow(ActorScheduleRow.Window schedule, int startHoursUtc, int endHoursUtc) throws SQLException {
        if (startHoursUtc < 0 || startHoursUtc > 24) {
            throw new IllegalArgumentException("startHoursUtc must be within [0,24]");
        }
        if (endHoursUtc < 0 || endHoursUtc > 24) {
            throw new IllegalArgumentException("endHoursUtc must be within [0,24]");
        }

        // Treat 24 as midnight
        startHoursUtc %= 24;
        endHoursUtc %= 24;


        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     UPDATE ACTOR_SCHEDULE SET START_HOURS_UTC = ?, END_HOURS_UTC = ?
                     WHERE SCHEDULE_NAME = ? AND SCHEDULE_TYPE = 'WINDOW'
                     """))
        {
            stmt.setInt(1, startHoursUtc);
            stmt.setInt(2, endHoursUtc);
            stmt.setString(3, schedule.name());

            if (stmt.executeUpdate() <= 0) {
                throw new SQLException("No window schedule found with name: " + schedule.name());
            }
        }
    }

    public void updateTrigger(ActorScheduleRow.Trigger schedule, int triggerHourUtc) throws SQLException {
        if (triggerHourUtc < 0 || triggerHourUtc > 24) {
            throw new IllegalArgumentException("triggerHourUtc must be within [0,24]");
        }

        // Treat 24 as midnight
        triggerHourUtc %= 24;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     UPDATE ACTOR_SCHEDULE SET START_HOURS_UTC = ?
                     WHERE SCHEDULE_NAME = ? AND SCHEDULE_TYPE = 'TRIGGER'
                     """))
        {
            stmt.setInt(1, triggerHourUtc);
            stmt.setString(2, schedule.name());

            if (stmt.executeUpdate() <= 0) {
                throw new SQLException("No trigger schedule found with name: " + schedule.name());
            }
        }
    }

    public void updateInterval(ActorScheduleRow.Interval schedule, int intervalHours) throws SQLException {
        if (intervalHours <= 0) {
            throw new IllegalArgumentException("intervalHours must be positive");
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     UPDATE ACTOR_SCHEDULE SET INTERVAL_HOURS = ?
                     WHERE SCHEDULE_NAME = ? AND SCHEDULE_TYPE = 'INTERVAL'
                     """))
        {
            stmt.setInt(1, intervalHours);
            stmt.setString(2, schedule.name());

            if (stmt.executeUpdate() <= 0) {
                throw new SQLException("No interval schedule found with name: " + schedule.name());
            }
        }
    }

}
