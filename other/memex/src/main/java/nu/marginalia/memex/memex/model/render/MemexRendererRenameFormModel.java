package nu.marginalia.memex.memex.model.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.memex.memex.renderer.MemexHtmlRenderer;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

@AllArgsConstructor @Getter
public class MemexRendererRenameFormModel implements MemexRendererableDirect {
    private final String doc;
    private final MemexRendererImageModel image;
    private final MemexNodeUrl url;
    private final String type;

    @Override
    public String render(MemexHtmlRenderer renderer) {
        return renderer.renderModel(this);
    }
}
