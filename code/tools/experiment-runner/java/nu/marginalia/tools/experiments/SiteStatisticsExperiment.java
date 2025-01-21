package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.tools.Experiment;

public class SiteStatisticsExperiment extends Experiment {


    @Inject
    public SiteStatisticsExperiment() {

    }

    @Override
    public boolean process(SerializableCrawlDataStream stream) {
        return true;
    }

    @Override
    public void onFinish() {
    }
}
