package nu.marginalia.schedule;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.schedule.ActorScheduleRow.*;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
public class ActorScheduleServiceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static ActorScheduleService service;

    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        service = new ActorScheduleService(dataSource);
    }

    @Test
    public void testGetAllSchedules() {
        List<ActorScheduleRow> all = service.getAllSchedules();
        assertEquals(7, all.size());
    }

    @Test
    public void testGetWindowSchedule() {
        WindowSchedule window = service.getWindow(ActorScheduleRow.Window.DOM_SAMPLE);
        assertEquals(16, window.startHoursUtc());
        assertEquals(20, window.endHoursUtc());
    }

    @Test
    public void testGetTriggerSchedule() {
        TriggerSchedule trigger = service.getTrigger(ActorScheduleRow.Trigger.MAINTENANCE);
        assertEquals(2, trigger.triggerHourUtc());
    }

    @Test
    public void testGetIntervalSchedule() {
        IntervalSchedule interval = service.getInterval(ActorScheduleRow.Interval.SCRAPE_FEEDS);
        assertEquals(6, interval.intervalHours());
    }

    @Test
    public void testUpdateWindow() throws SQLException {
        service.updateWindow(ActorScheduleRow.Window.DOM_SAMPLE, 14, 18);

        WindowSchedule updated = service.getWindow(ActorScheduleRow.Window.DOM_SAMPLE);
        assertEquals(14, updated.startHoursUtc());
        assertEquals(18, updated.endHoursUtc());

        // Restore original
        service.updateWindow(ActorScheduleRow.Window.DOM_SAMPLE, 16, 20);
    }

    @Test
    public void testUpdateTrigger() throws SQLException {
        service.updateTrigger(ActorScheduleRow.Trigger.MAINTENANCE, 5);

        TriggerSchedule updated = service.getTrigger(ActorScheduleRow.Trigger.MAINTENANCE);
        assertEquals(5, updated.triggerHourUtc());

        // Restore original
        service.updateTrigger(ActorScheduleRow.Trigger.MAINTENANCE, 2);
    }

    @Test
    public void testUpdateInterval() throws SQLException {
        service.updateInterval(ActorScheduleRow.Interval.SCRAPE_FEEDS, 12);

        IntervalSchedule updated = service.getInterval(ActorScheduleRow.Interval.SCRAPE_FEEDS);
        assertEquals(12, updated.intervalHours());

        // Restore original
        service.updateInterval(ActorScheduleRow.Interval.SCRAPE_FEEDS, 6);
    }

    @Test
    public void testUpdateWindowRejectsInvalidHours() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateWindow(ActorScheduleRow.Window.DOM_SAMPLE, -1, 20));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateWindow(ActorScheduleRow.Window.DOM_SAMPLE, 16, 25));
    }

    @Test
    public void testUpdateWindowNormalizes24ToZero() throws SQLException {
        service.updateWindow(ActorScheduleRow.Window.DOM_SAMPLE, 20, 24);

        WindowSchedule updated = service.getWindow(ActorScheduleRow.Window.DOM_SAMPLE);
        assertEquals(20, updated.startHoursUtc());
        assertEquals(0, updated.endHoursUtc());

        // Restore original
        service.updateWindow(ActorScheduleRow.Window.DOM_SAMPLE, 16, 20);
    }

    @Test
    public void testUpdateTriggerRejectsInvalidHours() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateTrigger(ActorScheduleRow.Trigger.MAINTENANCE, -1));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateTrigger(ActorScheduleRow.Trigger.MAINTENANCE, 25));
    }

    @Test
    public void testUpdateIntervalRejectsInvalidHours() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateInterval(ActorScheduleRow.Interval.SCRAPE_FEEDS, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateInterval(ActorScheduleRow.Interval.SCRAPE_FEEDS, -1));
    }
}
