package nu.marginalia.wmsa.edge.search.command;

import nu.marginalia.wmsa.configuration.server.Context;

import java.util.Optional;

public interface SearchCommandInterface {
    Optional<Object> process(Context ctx, SearchParameters parameters, String query);
}
