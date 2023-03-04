package nu.marginalia.renderer;

import java.io.IOException;

public class RendererFactory {

    public RendererFactory() {
    }

    public <T> MustacheRenderer<T> renderer(String template) throws IOException {
        return new MustacheRenderer<>(template);
    }
}
