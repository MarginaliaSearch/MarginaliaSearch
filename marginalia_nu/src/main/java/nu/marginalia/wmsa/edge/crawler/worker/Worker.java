package nu.marginalia.wmsa.edge.crawler.worker;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

public interface Worker extends Runnable {
    Histogram wmsa_edge_crawler_thread_run_times =
            Histogram.build("wmsa_edge_crawler_thread_run_times", "Run Times")
                    .register();
    Counter wmsa_edge_crawler_idle_worker =
            Counter.build("wmsa_edge_crawler_idle_worke", "No work, no money")
                    .register();

    void runCycle() throws InterruptedException;
}
