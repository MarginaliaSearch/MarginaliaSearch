package nu.marginalia.search.command;


import io.jooby.Context;

import java.util.Optional;

public interface SearchCommandInterface {
    Optional<Object> process(Context ctx, SearchParameters parameters);
}
