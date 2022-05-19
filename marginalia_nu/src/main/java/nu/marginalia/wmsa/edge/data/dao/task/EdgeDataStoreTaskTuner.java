package nu.marginalia.wmsa.edge.data.dao.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.util.concurrent.TimeUnit;

@Singleton
public class EdgeDataStoreTaskTuner {

    private static final Gauge wmsa_discover_queue_discover_quality_limit = Gauge.build("wmsa_discover_queue_discover_quality_limit",
            "wmsa_discover_queue_discover_quality_limit").register();
    private static final Gauge wmsa_discover_queue_index_quality_limit = Gauge.build("wmsa_discover_queue_index_quality_limit",
            "wmsa_discover_queue_index_quality_limit").register();
    private static final Gauge wmsa_discover_queue_discover_quality_pool_size = Gauge.build("wmsa_discover_queue_discover_quality_pool_size",
            "wmsa_discover_queue_discover_quality_pool_size").register();
    private static final Gauge wmsa_discover_queue_index_quality_pool_size = Gauge.build("wmsa_discover_queue_index_quality_pool_size",
            "wmsa_discover_queue_index_quality_pool_size").register();
    private static final Histogram wmsa_discover_queue_tune_time = Histogram.build("wmsa_discover_queue_tune_time",
            "wmsa_discover_queue_tune_time").register();
    private static final int INDEX_TARGET = 50;
    private static final int DISCOVER_TARGET = 100;


    private volatile double discoverQualityLimit = -2.;
    private volatile double indexQualityLimit = -2.;

    private final HikariDataSource dataSource;
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public EdgeDataStoreTaskTuner(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        Schedulers.io().schedulePeriodicallyDirect(this::tuneDiscoverQualityLimit, 1, 5, TimeUnit.SECONDS);
    }

    public double getIndexQualityLimit() {
        return indexQualityLimit;
    }
    public double getDiscoverQualityLimit() {
        return discoverQualityLimit;
    }

    @SneakyThrows
    private double linearSearchBounds(PreparedStatement stmt,
                                      int target,
                                      double delta,
                                      double min,
                                      double max) {
        for (double i = max; i >= min; i-=delta) {
            int cnt;
            stmt.setDouble(1, i);
            try (var rsp = stmt.executeQuery()) {
                rsp.next();
                cnt = rsp.getInt(1);
            }
            if (cnt >= target) {
                return i;
            }
        }

        return min;
    }
    @SneakyThrows
    private double binarySearchBounds(PreparedStatement stmt,
                                      int target,
                                      double eps,
                                      double min,
                                      double max) {
        while (max - min >= eps) {
            double v = (max + min)/2;
            stmt.setDouble(1, v);
            int cnt;
            try (var rsp = stmt.executeQuery()) {
                rsp.next();
                cnt = rsp.getInt(1);
            }

            if (cnt == target) {
                return v;
            } else if (cnt > target) {
                min = v;
            } else {
                max = v;
            }
        }
        return min;
    }

    @SneakyThrows
    private void tuneDiscoverQualityLimit() {
        var timer = wmsa_discover_queue_tune_time.startTimer();


        try (var connection = dataSource.getConnection()) {

            double delta = 0.1;
            double epsilon = 0.000001;
            try (var stmt =
                         connection.prepareStatement("SELECT COUNT(EC_DOMAIN.ID) FROM EC_DOMAIN USE INDEX(EC_DOMAIN_TRIO) WHERE DOMAIN_ALIAS IS NULL AND STATE = 0 AND QUALITY > ? AND INDEXED = 1")) {

                double lower = linearSearchBounds(stmt, INDEX_TARGET, delta, -100, 0);
                indexQualityLimit = binarySearchBounds(stmt, INDEX_TARGET, epsilon, lower, lower+delta);
                wmsa_discover_queue_index_quality_limit.set(indexQualityLimit);

                var rsp = stmt.executeQuery();
                rsp.next();
                wmsa_discover_queue_index_quality_pool_size.set(rsp.getInt(1));
            }


            try (var stmt =
                         connection.prepareStatement("SELECT COUNT(EC_DOMAIN.ID) FROM EC_DOMAIN USE INDEX(EC_DOMAIN_TRIO) INNER JOIN EC_TOP_DOMAIN ON EC_TOP_DOMAIN.ID=URL_TOP_DOMAIN_ID WHERE ALIVE = 1 AND DOMAIN_ALIAS IS NULL  AND STATE = 0 AND QUALITY > ? AND INDEXED = 0")) {

                double lower = linearSearchBounds(stmt, DISCOVER_TARGET, delta, -100, 0);
                discoverQualityLimit = binarySearchBounds(stmt, DISCOVER_TARGET, epsilon, lower, lower+delta);

                wmsa_discover_queue_discover_quality_limit.set(discoverQualityLimit);

                var rsp = stmt.executeQuery();
                rsp.next();
                wmsa_discover_queue_discover_quality_pool_size.set(rsp.getInt(1));
            }

        }
        catch (Exception ex) {
            logger.error("Failed to tune quality limits", ex);
        }

        timer.observeDuration();

    }
}
