package nu.marginalia.actor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public record ActorTimeslot(Instant start, Duration duration) {
    public ActorTimeslot {
        if (duration.isNegative() || duration.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("Invalid duration " + duration);
    }

    public static ActorSchedule LIVE_CRAWLER_SLOT = new ActorSchedule(0, 0 /* 3 */);
    public static ActorSchedule DOMAIN_PING_SLOT = new ActorSchedule(3, 9);
    public static ActorSchedule RSS_FEEDS_SLOT = new ActorSchedule(12, 12 /* 15 */);
    public static ActorSchedule DOM_SAMPLE_SLOT = new ActorSchedule(16, 20);
    public static ActorSchedule SCREENGRAB_SLOT_SAMPLE_SLOT = new ActorSchedule(20, 0);

    public Instant end() {
        return start.plus(duration);
    }

    public record ActorSchedule(int startHoursUtc, int endHoursUtc) {

        public ActorSchedule {
            if (startHoursUtc < 0 || startHoursUtc >= 24) throw new IllegalArgumentException("startHoursUtc must be within [0,24)");
            if (endHoursUtc < 0 || endHoursUtc >= 24) throw new IllegalArgumentException("endHoursUtc must be within [0,24)");
        }

        public ActorTimeslot nextTimeslot() {
            Instant now = Instant.now();
            Instant startTime = now
                    .atOffset(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.DAYS)
                    .plusHours(startHoursUtc)
                    .toInstant();

            int durationHours = endHoursUtc - startHoursUtc;
            if (durationHours < 0) {
                durationHours += 24;
            }

            if (startTime.isBefore(now)) {
                Instant endTime = startTime.plus(durationHours, ChronoUnit.HOURS);

                if (now.isBefore(endTime)) {
                    return new ActorTimeslot(now, Duration.between(now, endTime));
                }
                else {
                    startTime = startTime.plus(1, ChronoUnit.DAYS);
                }
            }
            else if (durationHours > (24 - startHoursUtc) && now.atOffset(ZoneOffset.UTC).getHour() < endHoursUtc) {
                Instant yesterdayStart = startTime.minus(1, ChronoUnit.DAYS);
                Instant endTime = yesterdayStart.plus(durationHours, ChronoUnit.HOURS);
                return new ActorTimeslot(now, Duration.between(now, endTime));
            }

            return new ActorTimeslot(startTime, Duration.ofHours(durationHours));
        }

    }

}
