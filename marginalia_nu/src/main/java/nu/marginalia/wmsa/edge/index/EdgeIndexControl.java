package nu.marginalia.wmsa.edge.index;


import com.google.inject.Inject;

import java.io.IOException;


public class EdgeIndexControl {

    private final IndexServicesFactory servicesFactory;

    @Inject
    public EdgeIndexControl(IndexServicesFactory servicesFactory) {
        this.servicesFactory = servicesFactory;
    }

    public void regenerateIndex() throws IOException {
        servicesFactory.convertIndex();

        System.gc();
    }

    public void switchIndexFiles() throws Exception {
        servicesFactory.switchFilesJob().call();
    }
}
