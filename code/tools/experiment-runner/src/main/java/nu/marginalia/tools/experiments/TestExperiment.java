package nu.marginalia.tools.experiments;

import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.tools.Experiment;
import nu.marginalia.tools.LegacyExperiment;

public class TestExperiment extends LegacyExperiment {
    @Override
    public boolean process(CrawledDomain domain) {
        return true;
    }

    @Override
    public void onFinish() {
        System.out.println("Tada!");
    }
}
