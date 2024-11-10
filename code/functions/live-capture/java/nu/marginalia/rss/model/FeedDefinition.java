package nu.marginalia.rss.model;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.ZonedDateTime;

public record FeedDefinition(
        String domain,
        String feedUrl,
        @Nullable String updated)
{

    private static final Duration defaultDuration = Duration.ofDays(30);

    public Duration durationSinceUpdated() {
        if (updated == null) { // Default to 30 days if no update time is available
            return defaultDuration;
        }

        try {
            return Duration.between(ZonedDateTime.parse(updated), ZonedDateTime.now());
        }
        catch (Exception e) {
            return defaultDuration;
        }
    }

}
