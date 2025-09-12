package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.model.DecoratedSearchResults;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.SearchParameters;

import java.util.Map;
import java.util.Optional;

public class SearchCommand implements SearchCommandInterface {
    private final SearchOperator searchOperator;


    @Inject
    public SearchCommand(SearchOperator searchOperator){
        this.searchOperator = searchOperator;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters) throws InterruptedException {
        DecoratedSearchResults results = searchOperator.doSearch(parameters);
        return Optional.of(new MapModelAndView("serp/main.jte",
                Map.of("results", results, "navbar", NavbarModel.SEARCH)
        ));
    }
}
