package nu.marginalia.search.command;


import spark.Response;

import java.util.Optional;

public interface SearchCommandInterface {
    Optional<Object> process(Response response, SearchParameters parameters);
}
