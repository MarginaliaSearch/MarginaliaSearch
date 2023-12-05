package nu.marginalia.search.command;


import nu.marginalia.client.Context;
import spark.Response;

import java.util.Optional;

public interface SearchCommandInterface {
    Optional<Object> process(Context ctx, Response response, SearchParameters parameters);
}
