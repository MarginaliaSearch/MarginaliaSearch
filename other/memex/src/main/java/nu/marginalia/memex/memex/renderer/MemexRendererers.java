package nu.marginalia.memex.memex.renderer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

@Singleton
public class MemexRendererers {
    private final MemexGmiRenderer gmiRenderer;
    private final MemexHtmlRenderer htmlRenderer;

    @Inject
    public MemexRendererers(MemexGmiRenderer gmiRenderer, MemexHtmlRenderer htmlRenderer) {
        this.gmiRenderer = gmiRenderer;
        this.htmlRenderer = htmlRenderer;
    }

    public void render(MemexNodeUrl url) {
        gmiRenderer.render(url);
        htmlRenderer.render(url);
    }
}
