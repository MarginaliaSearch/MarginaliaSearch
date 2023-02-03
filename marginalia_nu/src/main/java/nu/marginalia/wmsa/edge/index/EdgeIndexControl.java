package nu.marginalia.wmsa.edge.index;


import com.google.inject.Inject;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexSearchSetsService;

import java.io.IOException;


public class EdgeIndexControl {

    private final IndexServicesFactory servicesFactory;
    private final EdgeIndexSearchSetsService searchSetsService;

    @Inject
    public EdgeIndexControl(IndexServicesFactory servicesFactory, EdgeIndexSearchSetsService searchSetsService) {
        this.servicesFactory = servicesFactory;
        this.searchSetsService = searchSetsService;
    }

    public void regenerateIndex() throws IOException {
        servicesFactory.convertIndex(searchSetsService.getDomainRankings());

        System.gc();
    }

    public void switchIndexFiles() throws Exception {
        servicesFactory.switchFilesJob().call();
    }
}
