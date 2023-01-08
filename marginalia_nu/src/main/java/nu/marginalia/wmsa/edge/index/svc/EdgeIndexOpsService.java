package nu.marginalia.wmsa.edge.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.edge.index.postings.SearchIndexControl;
import spark.Request;
import spark.Response;
import spark.Spark;

@Singleton
public class EdgeIndexOpsService {

    private final SearchIndexControl indexes;
    private final EdgeOpsLockService opsLockService;
    private final EdgeIndexSearchSetsService searchSetService;

    @Inject
    public EdgeIndexOpsService(SearchIndexControl indexes,
                               EdgeOpsLockService opsLockService,
                               EdgeIndexSearchSetsService searchSetService) {
        this.indexes = indexes;
        this.opsLockService = opsLockService;
        this.searchSetService = searchSetService;
    }

    public Object repartitionEndpoint(Request request, Response response) throws Exception {

        if (!opsLockService.run(searchSetService::recalculateAll)) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    public Object reindexEndpoint(Request request, Response response) throws Exception {

        if (!indexes.reindex()) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

}
