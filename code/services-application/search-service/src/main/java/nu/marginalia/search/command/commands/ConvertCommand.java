package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.svc.SearchUnitConversionService;
import nu.marginalia.client.Context;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.Response;

import java.io.IOException;
import java.util.Map;

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
    public boolean process(Context ctx, Response response, SearchParameters parameters) {
        var conversion = searchUnitConversionService.tryConversion(ctx, parameters.query());
        if (conversion.isEmpty()) {
            return false;
        }

        conversionRenderer.renderInto(response, Map.of(
                "query", parameters.query(),
                "result", conversion.get(),
                "profile", parameters.profileStr())
        );

        return true;
    }
}
