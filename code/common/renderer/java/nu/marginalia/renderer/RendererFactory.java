package nu.marginalia.renderer;

import com.google.inject.Inject;
import nu.marginalia.renderer.config.HandlebarsConfigurator;

import java.io.IOException;

public class RendererFactory {

    private final HandlebarsConfigurator configurator;

    @Inject
    public RendererFactory(HandlebarsConfigurator configurator) {
        this.configurator = configurator;
    }

    /** Create a renderer for the given template */
    public <T> MustacheRenderer<T> renderer(String template) throws IOException {
        return new MustacheRenderer<>(configurator, template);
    }
}
