package nu.marginalia.memex.memex.model.fs;

import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.memex.model.MemexImage;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MemexFileSystem {
    private final Map<MemexNodeUrl, MemexDirectory> fileSystemContentsByDir = new ConcurrentHashMap<>();

    public MemexFileSystem() {
    }

    public Optional<MemexDirectory> get(MemexNodeUrl url) {
        return Optional.ofNullable(fileSystemContentsByDir.get(url));
    }
    public List<GemtextDocument> getDocuments(MemexNodeUrl url) {
        var contents = fileSystemContentsByDir.get(url);
        if (contents == null) {
            return Collections.emptyList();
        }
        var list = new ArrayList<>(contents.getDocuments().values());
        list.sort(Comparator.comparing(GemtextDocument::getUrl));
        return list;
    }

    public List<MemexImage> getImages(MemexNodeUrl url) {
        var contents = fileSystemContentsByDir.get(url);
        if (contents == null) {
            return Collections.emptyList();
        }
        var list = new ArrayList<>(contents.getImages().values());
        list.sort(Comparator.comparing(MemexImage::getPath));
        return list;
    }

    public List<MemexNodeUrl> getSubdirs(MemexNodeUrl url) {
        var contents = fileSystemContentsByDir.get(url);
        if (contents == null) {
            return Collections.emptyList();
        }
        var list = new ArrayList<>(contents.getSubdirs().keySet());
        list.sort(Comparator.naturalOrder());
        return list;
    }

    public void recalculateDirectories() {
        fileSystemContentsByDir.forEach((k, v) -> {
            var parent = k.getParentUrl();
            if (parent != null) {
                registerDir(k.getParentUrl()).getSubdirs().put(k, v);
            }
        });
    }

    public MemexDirectory registerDir(MemexNodeUrl url) {
        return fileSystemContentsByDir
                .computeIfAbsent(url, p -> new MemexDirectory());
    }

    public void register(MemexImage image) {
        registerDir(image.path.getParentUrl())
                .getImages()
                .put(image.path, image);
    }

    public void register(GemtextDocument document) {
        registerDir(document.getUrl().getParentUrl())
                .getDocuments()
                .put(document.getUrl(), document);
    }

    public void remove(MemexNodeUrl url) {
        var contents = fileSystemContentsByDir.get(url.getParentUrl());
        contents.removeDocument(url);
        contents.removeImage(url);
    }

    public List<MemexNodeUrl> getAllDirectories() {
        return new ArrayList<>(fileSystemContentsByDir.keySet());
    }

    public boolean isDirectory(MemexNodeUrl url) {
        return fileSystemContentsByDir.containsKey(url);
    }
}
