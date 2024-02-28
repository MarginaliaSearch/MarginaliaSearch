package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.svc.SearchUnitConversionService;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.Response;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class ConvertCommand implements SearchCommandInterface {
    private final SearchUnitConversionService searchUnitConversionService;
    private final MustacheRenderer<Map<String, String>> conversionRenderer;

    @Inject
    public ConvertCommand(SearchUnitConversionService searchUnitConversionService, RendererFactory rendererFactory) throws IOException {
        this.searchUnitConversionService = searchUnitConversionService;

        conversionRenderer = rendererFactory.renderer("search/conversion-results");
    }

    @Override
    @SneakyThrows
    public Optional<Object> process(Response response, SearchParameters parameters) {
        var conversion = searchUnitConversionService.tryConversion(parameters.query());
        return conversion.map(s -> conversionRenderer.render(Map.of(
                "query", parameters.query(),
                "result", s,
                "profile", parameters.profileStr())
        ));

    }
}
