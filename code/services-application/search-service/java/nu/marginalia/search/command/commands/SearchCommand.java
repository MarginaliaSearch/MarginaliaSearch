package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.search.JteRenderer;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.DecoratedSearchResults;
import nu.marginalia.search.model.NavbarModel;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class SearchCommand implements SearchCommandInterface {
    private final SearchOperator searchOperator;
    private final JteRenderer jteRenderer;


    @Inject
    public SearchCommand(SearchOperator searchOperator,
                         JteRenderer jteRenderer) throws IOException {
        this.searchOperator = searchOperator;
        this.jteRenderer = jteRenderer;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters) throws InterruptedException {
        DecoratedSearchResults results = searchOperator.doSearch(parameters);
        return Optional.of(new MapModelAndView("serp/main.jte",
                Map.of("results", results, "navbar", NavbarModel.SEARCH)
        ));
    }
}
