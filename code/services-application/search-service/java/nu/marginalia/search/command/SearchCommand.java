package nu.marginalia.search.command;

import com.google.inject.Inject;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.DecoratedSearchResults;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.SearchParameters;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class SearchCommand implements SearchCommandInterface {
    private final SearchOperator searchOperator;
    private final LanguageConfiguration languageConfiguration;


    @Inject
    public SearchCommand(SearchOperator searchOperator, LanguageConfiguration languageConfiguration) {
        this.searchOperator = searchOperator;
        this.languageConfiguration = languageConfiguration;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters) throws InterruptedException, TimeoutException {
        DecoratedSearchResults results = searchOperator.doSearch(parameters);
        return Optional.of(new MapModelAndView("serp/main.jte",
                Map.of("results", results,
                        "navbar", NavbarModel.SEARCH,
                        "languageDefinitions", languageConfiguration.languagesMap()
                )
        ));
    }
}
