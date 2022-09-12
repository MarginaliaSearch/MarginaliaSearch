package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.search.command.SearchCommandInterface;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.svc.EdgeSearchUnitConversionService;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class ConvertCommand implements SearchCommandInterface {
    private final EdgeSearchUnitConversionService edgeSearchUnitConversionService;
    private final MustacheRenderer<Map<String, String>> conversionRenderer;

    @Inject
    public ConvertCommand(EdgeSearchUnitConversionService edgeSearchUnitConversionService, RendererFactory rendererFactory) throws IOException {
        this.edgeSearchUnitConversionService = edgeSearchUnitConversionService;

        conversionRenderer = rendererFactory.renderer("edge/conversion-results");
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        var conversion = edgeSearchUnitConversionService.tryConversion(ctx, query);
        if (conversion.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(conversionRenderer.render(Map.of("query", query, "result", conversion.get(), "profile", parameters.profileStr())));
    }
}
