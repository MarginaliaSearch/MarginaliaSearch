package nu.marginalia.status.endpoints;

import nu.marginalia.status.StatusMetric;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("flaky")
public class MainSearchEndpointTest {

    @Test
    // This test will be flaky if the search service is down
    public void checkMarginaliaSearchUp__Flaky() {
        MainSearchEndpoint mainSearchEndpoint = new MainSearchEndpoint("https://search.marginalia.nu/search?query=plato&ref=marginalia-automatic-metrics");
        StatusMetric statusMetric = new StatusMetric("MainSearch", mainSearchEndpoint::check);

        var result = statusMetric.update();
        assertInstanceOf(StatusMetric.MeasurementResult.Success.class, result);
        assertTrue(mainSearchEndpoint.check());
    }

    @Test
    // This test will be flaky if the search service is down
    public void checkMarginaliaSearchDown__Flaky() {
        // malformed query parameter:
        MainSearchEndpoint mainSearchEndpoint = new MainSearchEndpoint("https://search.marginalia.nu/search?plato&ref=marginalia-automatic-metrics");
        StatusMetric statusMetric = new StatusMetric("MainSearch", mainSearchEndpoint::check);

        var result = statusMetric.update();
        assertInstanceOf(StatusMetric.MeasurementResult.Failure.class, result);
        assertFalse(mainSearchEndpoint.check());
    }
}