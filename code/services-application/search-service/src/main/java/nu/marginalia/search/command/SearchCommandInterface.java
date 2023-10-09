package nu.marginalia.search.command;


import nu.marginalia.client.Context;

import java.util.Optional;

public interface SearchCommandInterface {
    Optional<Object> process(Context ctx, SearchParameters parameters, String query);
}
