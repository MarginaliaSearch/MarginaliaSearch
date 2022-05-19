package nu.marginalia.wmsa.memex.model.fs;

import lombok.Getter;
import nu.marginalia.gemini.gmi.GemtextDocument;
import nu.marginalia.wmsa.memex.model.MemexImage;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;

import java.util.HashMap;
import java.util.Map;

@Getter
public class MemexDirectory {
    private final Map<MemexNodeUrl, GemtextDocument> documents;
    private final Map<MemexNodeUrl, MemexImage> images;
    private final Map<MemexNodeUrl, MemexDirectory> subdirs;

    public MemexDirectory() {
        documents = new HashMap<>();
        images = new HashMap<>();
        subdirs = new HashMap<>();
    }

    public void removeDocument(MemexNodeUrl url) {
        documents.remove(url);
    }

    public void removeImage(MemexNodeUrl url) {
        images.remove(url);
    }
}
