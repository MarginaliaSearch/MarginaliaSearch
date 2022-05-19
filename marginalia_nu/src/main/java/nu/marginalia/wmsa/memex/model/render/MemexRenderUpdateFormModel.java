package nu.marginalia.wmsa.memex.model.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.wmsa.memex.renderer.MemexHtmlRenderer;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;

@AllArgsConstructor @Getter
public class MemexRenderUpdateFormModel implements MemexRendererableDirect {
    public final MemexNodeUrl url;
    public final String title;
    public final String section;
    public final String text;

    @Override
    public String render(MemexHtmlRenderer renderer) {
        return renderer.renderModel(this);
    }
}
