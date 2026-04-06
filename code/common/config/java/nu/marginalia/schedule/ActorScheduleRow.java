package nu.marginalia.schedule;

/** Represents a configured schedule for a periodic actor. */
public sealed interface ActorScheduleRow {
    String scheduleName();
    String description();

    enum Window { DOMAIN_PING, DOM_SAMPLE, SCREENGRAB }
    enum Trigger { LIVE_CRAWLER, RSS_FEEDS, MAINTENANCE }
    enum Interval { SCRAPE_FEEDS }

    /** Runs continuously during a UTC hour window, e.g. 16:00-20:00.
     *  The actor is active for the duration of the window and sleeps until
     *  the next day's window. */
    record WindowSchedule(String scheduleName, int startHoursUtc, int endHoursUtc,
                          String description) implements ActorScheduleRow {}

    /** Triggers at a specific UTC hour and runs until its work is done.
     *  The run time is determined by the task, not the schedule. */
    record TriggerSchedule(String scheduleName, int triggerHourUtc,
                           String description) implements ActorScheduleRow {}

    /** Repeats on a fixed interval regardless of time of day. */
    record IntervalSchedule(String scheduleName, int intervalHours,
                            String description) implements ActorScheduleRow {}
}
