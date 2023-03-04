package nu.marginalia.memex.memex.model.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.memex.model.MemexLink;

import java.util.List;

@AllArgsConstructor @Getter
public class MemexRendererViewModel {
    public final GemtextDocument baseDoc;
    public final String title;
    public final List<MemexLink> backlinks;
    public final String doc;
    public final String parent;

    public String getParent() {
        if ("/".equals(parent) || parent.isBlank()) {
            return null;
        }
        return parent;
    }
}
