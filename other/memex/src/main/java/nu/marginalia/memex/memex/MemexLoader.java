package nu.marginalia.memex.memex;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.memex.gemini.gmi.GemtextDatabase;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.memex.model.MemexImage;
import nu.marginalia.memex.memex.model.MemexNode;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import nu.marginalia.memex.memex.system.MemexFileSystemModifiedTimes;
import nu.marginalia.memex.memex.system.MemexSourceFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class MemexLoader {
    private final MemexData data;
    private final MemexFileSystemModifiedTimes modifiedTimes;
    private final Path root;
    private final MemexSourceFileSystem sourceFileSystem;

    private final String tombstonePath;
    private final String redirectsPath;

    private static final Logger logger = LoggerFactory.getLogger(MemexLoader.class);

    @Inject
    public MemexLoader(MemexData data,
                       MemexFileSystemModifiedTimes modifiedTimes,
                       MemexSourceFileSystem sourceFileSystem,
                       @Named("memex-root") Path root,
                       @Named("tombestone-special-file") String tombstonePath,
                       @Named("redirects-special-file") String redirectsPath) {

        this.data = data;
        this.modifiedTimes = modifiedTimes;
        this.sourceFileSystem = sourceFileSystem;
        this.root = root;
        this.tombstonePath = tombstonePath;
        this.redirectsPath = redirectsPath;
    }


    public void load() throws IOException {

        loadTombstones();
        loadRedirects();

        try (var files = Files.walk(root)) {
            files.forEach(this::loadFile);
        }

        data.getFilesystem().recalculateDirectories();

    }

    private void loadFile(Path p) {
        var file = p.toFile();

        try {
            if (p.toString().contains(".git")) {
                return;
            }
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                data.getFilesystem().registerDir(MemexNodeUrl.ofRelativePath(root, p));
            } else if (isGemtext(file)) {
                loadNode(p);
            } else if (isImage(file)) {
                loadImage(p);
            }
        }
        catch (IOException ex) {
            logger.error("Failed to load file " + p, ex);
        }
    }

    public void loadImage(Path p) throws IOException {
        if (!modifiedTimes.isFreshUpdate(p)) {
            return;
        }

        var url = MemexNodeUrl.ofRelativePath(root, p);
        data.addImage(url, new MemexImage(url, p));
        logger.info("Loading {}", p);
    }

    public Set<MemexNodeUrl> loadTombstones() {
        var oldValues = data.getTombstones();
        var newValues = loadGemtextDb(Path.of(root + tombstonePath));

        newValues.ifPresent(data::setTombstones);


        if (newValues.isPresent()) {
            if (oldValues.isPresent()) {
                var oldTs = oldValues.get();
                var newTs = newValues.get();
                return oldTs.difference(newTs);
            }
        }

        return Collections.emptySet();
    }

    public Set<MemexNodeUrl> loadRedirects() {
        var oldValues = data.getTombstones();
        var newValues = loadGemtextDb(Path.of(root + redirectsPath));

        newValues.ifPresent(data::setRedirects);

        if (newValues.isPresent()) {
            if (oldValues.isPresent()) {
                var oldTs = oldValues.get();
                var newTs = newValues.get();
                return oldTs.difference(newTs);
            }
        }

        return Collections.emptySet();
    }

    private Optional<GemtextDatabase> loadGemtextDb(Path p) {
        if (Files.exists(p)) {
            try {
                return Optional.of(GemtextDatabase.of(MemexNodeUrl.ofRelativePath(root, p), p));
            } catch (IOException e) {
                logger.error("Failed to load database " + p, e);
            }
        }
        return Optional.empty();
    }

    private boolean isGemtext(File f) {
        return f.isFile() && f.getName().endsWith(".gmi");
    }

    private boolean isImage(File f) {
        return f.isFile() && f.getName().endsWith(".png");
    }

    @CheckReturnValue
    public Collection<MemexNodeUrl> updateNode(MemexNodeUrl url, String contents) throws IOException {
        sourceFileSystem.replaceFile(url, contents);
        return loadNode(url);
    }

    @CheckReturnValue
    public Collection<MemexNodeUrl> createNode(MemexNodeUrl url, String contents) throws IOException {
        sourceFileSystem.createFile(url, contents);
        return loadNode(url);
    }


    public MemexImage uploadImage(MemexNodeUrl url, byte[] bytes) throws IOException {
        sourceFileSystem.createFile(url, bytes);

        var img = new MemexImage(url, url.asAbsolutePath(root));
        data.addImage(url, img);
        return img;
    }


    public Set<MemexNodeUrl> reloadImage(MemexNodeUrl url) throws IOException {
        var path = url.asAbsolutePath(root);
        if (!Files.exists(path)) {
            return data.deleteImage(url);
        }
        else {
            loadImage(path);
            Set<MemexNodeUrl> affectedUrls = new HashSet<>();
            affectedUrls.add(url);

            for (var u = url.getParentUrl(); u != null; u = u.getParentUrl()) {
                affectedUrls.add(u);
            }

            return affectedUrls;
        }
    }

    public Set<MemexNodeUrl> reloadNode(MemexNodeUrl url) throws IOException {
        var path = url.asAbsolutePath(root);
        if (!Files.exists(path)) {
            return data.deleteDocument(url);
        }
        else {
            return loadNode(path);
        }
    }

    public Set<MemexNodeUrl> loadNode(Path path) throws IOException {

        if (!modifiedTimes.isFreshUpdate(path)) {
            return Set.of(MemexNodeUrl.ofRelativePath(root, path));
        }

        logger.info("Loading {}", path);

        return loadNode(MemexNodeUrl.ofRelativePath(root, path));
    }

    public Set<MemexNodeUrl> loadNode(MemexNodeUrl url) throws IOException {

        var doc = GemtextDocument.of(url, url.asAbsolutePath(root));

        data.addDocument(url, doc);

        Set<MemexNodeUrl> urlsAffected = data.getNeighbors(url);

        data.updateOutlinks(url, doc);

        urlsAffected.addAll(data.getNeighbors(url));
        urlsAffected.add(url);
        urlsAffected.removeIf(u -> null == data.getDocument(u));

        for (var u = url.getParentUrl(); u != null; u = u.getParentUrl()) {
            urlsAffected.add(u);
        }

        return urlsAffected;
    }

    public Set<MemexNodeUrl> delete(MemexNode node) throws IOException {
        sourceFileSystem.delete(node.getUrl());
        return node.visit(new MemexNode.MemexNodeVisitor<>() {
            @Override
            public Set<MemexNodeUrl> onDocument(MemexNodeUrl url) {
                return data.deleteDocument(url);
            }

            @Override
            public Set<MemexNodeUrl> onImage(MemexNodeUrl url) {
                return data.deleteImage(url);
            }
        });
    }

    public Set<MemexNodeUrl> rename(MemexNode src, MemexNodeUrl dst) throws IOException {
        sourceFileSystem.renameFile(src.getUrl(), dst);
        return src.visit(new MemexNode.MemexNodeVisitor<Set<MemexNodeUrl>>() {
            @Override
            public Set<MemexNodeUrl> onDocument(MemexNodeUrl url) throws IOException {
                var changes = data.deleteDocument(url);
                return Sets.union(changes, reloadNode(dst));
            }

            @Override
            public Set<MemexNodeUrl> onImage(MemexNodeUrl url) throws IOException {
                var changes = data.deleteImage(url);
                return Sets.union(changes, reloadImage(dst));
            }
        });

    }

    public byte[] getRaw(MemexNodeUrl url) throws IOException {
        return sourceFileSystem.getRaw(url);
    }
}
