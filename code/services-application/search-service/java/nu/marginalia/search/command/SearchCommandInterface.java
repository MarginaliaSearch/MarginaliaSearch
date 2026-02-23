package nu.marginalia.search.command;

import io.jooby.Context;
import io.jooby.ModelAndView;
import nu.marginalia.search.model.SearchParameters;

import java.util.Optional;

public interface SearchCommandInterface {
    Optional<ModelAndView<?>> process(SearchParameters parameters, Context ctx) throws Exception;
}
