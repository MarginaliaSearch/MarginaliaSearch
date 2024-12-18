package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import nu.marginalia.search.JteRenderer;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.svc.SearchUnitConversionService;

import java.util.Map;
import java.util.Optional;

public class ConvertCommand implements SearchCommandInterface {
    private final SearchUnitConversionService searchUnitConversionService;
    private final JteRenderer renderer;

    @Inject
    public ConvertCommand(SearchUnitConversionService searchUnitConversionService,
                          JteRenderer renderer) {
        this.searchUnitConversionService = searchUnitConversionService;

        this.renderer = renderer;
    }

    @Override
    public Optional<ModelAndView<?>> process(SearchParameters parameters) {
        var conversion = searchUnitConversionService.tryConversion(parameters.query());
        return conversion.map(s -> new MapModelAndView("serp/unit-conversion.jte",
                Map.of(
                "parameters", parameters,
                "navbar", NavbarModel.SEARCH,
                "result", s)
        ));

    }
}
