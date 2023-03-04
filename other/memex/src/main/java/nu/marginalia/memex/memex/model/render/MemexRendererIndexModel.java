package nu.marginalia.memex.memex.model.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.memex.memex.model.MemexImage;
import nu.marginalia.memex.memex.model.MemexIndexTask;
import nu.marginalia.memex.memex.model.MemexLink;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public class MemexRendererIndexModel {
    public final MemexNodeUrl url;
    public final List<GemtextDocument> docs;
    public final List<MemexImage> images;
    public final List<MemexNodeUrl> directories;
    public final List<MemexIndexTask> tasks;
    public final List<MemexLink> backlinks;
    
    public String getFilename() {
        return url.getFilename();
    }

    public MemexNodeUrl getParent() {
        return url.getParentUrl();
    }

    public List<GemtextDocument> getDocs() {
        return docs.stream()
                .filter(doc -> !doc.isIndex())
                .sorted(Comparator.comparing(GemtextDocument::getUrl).reversed())
                .collect(Collectors.toList());
    }

    public final String getTitle() {
        return Optional.ofNullable(getIndexDocument()).map(GemtextDocument::getTitle).orElse(url.toString());
    }

    public GemtextDocument getDocument(String filename) {
        return docs.stream().filter(doc -> doc.getUrl().getFilename().endsWith(filename)).findFirst().orElse(null);
    }

    private GemtextDocument getIndexDocument() {
        return getDocument("index.gmi");
    }

    public String getIndexData() {
        var indexDoc = getIndexDocument();
        if (indexDoc == null) {
            return null;
        }
        var htmlRenderer = new GemtextRendererFactory("").htmlRendererReadOnly();
        return indexDoc.render(htmlRenderer);
    }

    public boolean hasPragma(String value) {
        var doc = getIndexDocument();
        if (doc == null) {
            return false;
        }
        return doc.getPragmas().contains(value);

    }
}
