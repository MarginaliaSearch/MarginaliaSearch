package nu.marginalia.search.command;


import nu.marginalia.client.Context;
import spark.Response;

public interface SearchCommandInterface {
    boolean process(Context ctx, Response response, SearchParameters parameters);
}
