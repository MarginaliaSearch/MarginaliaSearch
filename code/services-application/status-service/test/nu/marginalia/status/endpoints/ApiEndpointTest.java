package nu.marginalia.status.endpoints;

import nu.marginalia.status.StatusMetric;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ApiEndpointTest {

    @Test
    // This test will be flaky if the search service is down
    public void checkMarginaliaSearchUp__Flaky() {
        ApiEndpoint mainSearchChecker = new ApiEndpoint("https://api.marginalia.nu/public/search/plato");
        StatusMetric statusMetric = new StatusMetric("MainSearch", mainSearchChecker::check);

        var result = statusMetric.update();
        assertInstanceOf(StatusMetric.MeasurementResult.Success.class, result);
        assertTrue(mainSearchChecker.check());
    }

    @Test
    // This test will be flaky if the search service is down
    public void checkMarginaliaSearchDown__Flaky() {
        // malformed query parameter:
        ApiEndpoint mainSearchChecker = new ApiEndpoint("https://api.marginalia.nu/qqweqwe/search/plato");
        StatusMetric statusMetric = new StatusMetric("MainSearch", mainSearchChecker::check);

        var result = statusMetric.update();
        assertInstanceOf(StatusMetric.MeasurementResult.Failure.class, result);
        assertFalse(mainSearchChecker.check());
    }
}