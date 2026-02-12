package nu.marginalia.actor;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class ActorTimeslot {

    /** Return the first future instant, at exactly `hour` past midnight UTC
     */
    public static Instant dailyAtHourUTC(int hour) {
        Instant now = Instant.now();
        Instant startTime = now
                .atOffset(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .plusHours(hour)
                .toInstant();

        if (startTime.isBefore(now)) {
            startTime = startTime.plus(1, ChronoUnit.DAYS);
        }

        return startTime;
    }

}
