package nu.marginalia.search.command;

import com.google.inject.Inject;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.search.JteRenderer;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.model.SearchParameters;
import nu.marginalia.search.svc.SearchUnitConversionService;

import java.util.Map;
import java.util.Optional;

public class ConvertCommand implements SearchCommandInterface {
    private final SearchUnitConversionService searchUnitConversionService;
    private final LanguageConfiguration languageConfiguration;
    private final JteRenderer renderer;

    @Inject
    public ConvertCommand(SearchUnitConversionService searchUnitConversionService,
                          LanguageConfiguration languageConfiguration,
                          JteRenderer renderer) {
        this.searchUnitConversionService = searchUnitConversionService;
        this.languageConfiguration = languageConfiguration;

        this.renderer = renderer;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters) {
        var conversion = searchUnitConversionService.tryConversion(parameters.query());
        return conversion.map(s -> new MapModelAndView("serp/unit-conversion.jte",
                Map.of(
                "parameters", parameters,
                "navbar", NavbarModel.SEARCH,
                "languageDefinitions", languageConfiguration.languagesMap(),
                "result", s)
        ));

    }
}
