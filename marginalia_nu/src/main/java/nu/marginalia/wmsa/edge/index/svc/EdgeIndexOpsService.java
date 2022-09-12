package nu.marginalia.wmsa.edge.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import spark.Request;
import spark.Response;
import spark.Spark;

@Singleton
public class EdgeIndexOpsService {

    private final SearchIndexes indexes;

    @Inject
    public EdgeIndexOpsService(SearchIndexes indexes) {
        this.indexes = indexes;
    }

    public Object repartitionEndpoint(Request request, Response response) {

        if (!indexes.repartition()) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    public Object preconvertEndpoint(Request request, Response response) {
        if (!indexes.preconvert()) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    public Object reindexEndpoint(Request request, Response response) {
        int id = Integer.parseInt(request.params("id"));

        if (!indexes.reindex(id)) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

}
