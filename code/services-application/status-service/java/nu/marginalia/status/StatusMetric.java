package nu.marginalia.status;

import io.prometheus.metrics.core.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class StatusMetric {
    private final String name;
    private final Supplier<Boolean> checker;

    // We also push the metrics to Prometheus for additional statistics and monitoring

    private static final Counter successCounter = Counter.builder().name("external_status_metric_success").help("Status Metric Success")
            .labelNames("name")
            .register();
    private static final Counter failureCounter = Counter.builder().name("external_status_metric_failure").help("Status Metric Failure")
            .labelNames("name")
            .register();

    private static final Logger logger = LoggerFactory.getLogger(StatusMetric.class);

    public StatusMetric(String name, Supplier<Boolean> checker) {
        this.name = name;
        this.checker = checker;
    }

    public String getName() {
        return name;
    }

    public MeasurementResult update() {
        long startTime = System.nanoTime();
        Instant now = Instant.now();

        try {
            if (checker.get()) {
                long endTime = System.nanoTime();
                successCounter.labelValues(name).inc();
                return new MeasurementResult.Success(name, now, Duration.ofNanos(endTime - startTime));
            }
        } catch (Exception e) {
            logger.error("Failed to check status metric: " + name, e);
        }

        failureCounter.labelValues(name).inc();

        return new MeasurementResult.Failure(name, now);
    }

    public sealed interface MeasurementResult {
        String name();
        Instant when();

        record Success(String name, Instant when, Duration callDuration) implements MeasurementResult {}
        record Failure(String name, Instant when) implements MeasurementResult {}
    }
}
