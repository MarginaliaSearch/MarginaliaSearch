package nu.marginalia.ping;

import nu.marginalia.ping.model.ErrorClassification;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class BackoffStrategyTest {

    @Test
    void getUpdateTime() {

        var strategy = new BackoffStrategy(
                PingModule.createPingIntervalsConfiguration()
        );

        for (var errorClassification : ErrorClassification.values()) {
            System.out.println("Testing error classification: " + errorClassification);
            Duration current = null;
            int retries = 0;

            for (int i = 0; i < 20; i++) {
                Duration nextUpdate = strategy.getUpdateTime(current, errorClassification, retries);
                System.out.println("Next update time: " + nextUpdate);
                current = nextUpdate;
                retries++;
            }
        }
    }
}