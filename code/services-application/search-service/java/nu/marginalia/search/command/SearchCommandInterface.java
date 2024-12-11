package nu.marginalia.search.command;

import io.jooby.ModelAndView;

import java.util.Optional;

public interface SearchCommandInterface {
    Optional<ModelAndView<?>> process(SearchParameters parameters) throws Exception;
}
