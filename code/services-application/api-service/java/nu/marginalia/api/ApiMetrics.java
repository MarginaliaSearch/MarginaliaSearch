package nu.marginalia.api;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

public class ApiMetrics {

    public static final Counter wmsa_api_timeout_count = Counter.build()
            .name("wmsa_api_timeout_count")
            .labelNames("key")
            .help("API timeout count")
            .register();
    public static final Counter wmsa_api_cache_hit_count = Counter.build()
            .name("wmsa_api_cache_hit_count")
            .labelNames("key")
            .help("API cache hit count")
            .register();
    public static final Histogram wmsa_api_query_time = Histogram.build()
            .name("wmsa_api_query_time")
            .labelNames("key")
            .linearBuckets(0.05, 0.05, 15)
            .help("API-side query time")
            .register();
}
