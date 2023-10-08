package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.client.Context;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import spark.Request;
import spark.Response;
import spark.Spark;

public class QueryService extends Service {

    private final IndexClient indexClient;
    private final Gson gson;

    @Inject
    public QueryService(BaseServiceParams params,
                        IndexClient indexClient,
                        Gson gson)
    {
        super(params);
        this.indexClient = indexClient;
        this.gson = gson;

        Spark.post("/delegate/", this::delegateToIndex, gson::toJson);
    }

    private SearchResultSet delegateToIndex(Request request, Response response) {
        String json = request.body();
        SearchSpecification specsSet = gson.fromJson(json, SearchSpecification.class);

        response.type("application/json");

        return indexClient.query(Context.fromRequest(request), specsSet);
    }

}
