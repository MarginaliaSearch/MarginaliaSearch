package nu.marginalia.tools.experiments;

import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.tools.Experiment;

public class TestExperiment implements Experiment {
    @Override
    public boolean process(CrawledDomain domain) {
        return true;
    }

    @Override
    public void onFinish() {
        System.out.println("Tada!");
    }
}
