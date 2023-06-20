package nu.marginalia.tools;

import nu.marginalia.crawling.model.CrawledDomain;

import java.util.HashSet;
import java.util.Set;

public abstract class Experiment {
    protected Set<String> domains = new HashSet<>();

    public void args(String... args) {
        for (String domain : args) {
            domains.add(domain.toLowerCase());
        }
    };

    /** The experiment processes the domain here.
     *
     * @return true to continue, false to terminate.
     */
    public abstract boolean process(CrawledDomain domain);

    /** Invoked after all domains are processed
     *
     */
    public void onFinish() {}

    public boolean isInterested(String domainName) {
        if (domains.isEmpty())
            return true;

        return domains.contains(domainName.toLowerCase());
    }
}
