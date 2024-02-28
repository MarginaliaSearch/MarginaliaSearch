package nu.marginalia.tools;

import nu.marginalia.crawling.io.SerializableCrawlDataStream;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class Experiment {
    protected Set<String> domains = new HashSet<>();

    public void args(String... args) {
        for (String domain : args) {
            domains.add(domain.toLowerCase());
        }
    }

    public abstract boolean process(SerializableCrawlDataStream dataStream) throws IOException;

    /** Invoked after all domains are processed
     *
     */
    public void onFinish() {}
}
