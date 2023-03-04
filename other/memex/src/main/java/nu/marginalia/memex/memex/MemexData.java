package nu.marginalia.memex.memex;

import com.google.inject.Singleton;
import nu.marginalia.memex.gemini.gmi.GemtextDatabase;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.memex.model.MemexImage;
import nu.marginalia.memex.memex.model.MemexLink;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import nu.marginalia.memex.memex.model.fs.MemexFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;

@Singleton
public class MemexData {
    private final MemexLinks links = new MemexLinks();
    private final Map<MemexNodeUrl, GemtextDocument> documents = new HashMap<>();

    private final Map<MemexNodeUrl, MemexImage> images = new HashMap<>();
    private final MemexFileSystem fileSystem = new MemexFileSystem();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private GemtextDatabase tombstones = null;
    private GemtextDatabase redirects = null;

    public synchronized Collection<MemexImage> getImages() {
        return new ArrayList<>(images.values());
    }
    public synchronized Collection<GemtextDocument> getDocuments() { return new ArrayList<>(documents.values()); }

    public synchronized void setTombstones(GemtextDatabase tombstones) {
        this.tombstones = tombstones;
    }
    public synchronized void setRedirects(GemtextDatabase redirects) {
        this.redirects = redirects;
    }

    public synchronized void addDocument(MemexNodeUrl url, GemtextDocument doc) {
        logger.debug("addDocument({})", url);
        documents.put(url, doc);
        fileSystem.register(doc);
    }

    public synchronized void addImage(MemexNodeUrl url, MemexImage img) {
        images.put(url, img);
        fileSystem.register(img);
    }

    public Optional<GemtextDatabase> getTombstones() {
        return Optional.ofNullable(tombstones);
    }
    public Optional<GemtextDatabase> getRedirects() {
        return Optional.ofNullable(redirects);
    }

    public synchronized void updateOutlinks(MemexNodeUrl url, GemtextDocument doc) {

        var linksForNode = new TreeSet<>(Comparator.comparing(MemexLink::getDest));

        MemexNodeUrl srcUrl = "index.gmi".equals(url.getFilename()) ? url.getParentUrl() : url;

        for (var link : doc.getLinks()) {
            link.getUrl().visitNodeUrl(nodeUrl ->
                    linksForNode.add(new MemexLink(nodeUrl, srcUrl, doc.getTitle(), doc.getHeadingForElement(link), link.getHeading()))
            );
        }

        links.setOutlinks(srcUrl, linksForNode);
    }

    public synchronized Set<MemexNodeUrl> getNeighbors(MemexNodeUrl url) {
        return links.getNeighbors(url);
    }

    public synchronized void forEach(BiConsumer<MemexNodeUrl, GemtextDocument> consumer) {
        documents.forEach(consumer);
    }

    public synchronized GemtextDocument getDocument(MemexNodeUrl url) {
        return documents.get(url);
    }

    public synchronized MemexImage getImage(MemexNodeUrl url) {
        return images.get(url);
    }
    public synchronized List<MemexLink> getBacklinks(MemexNodeUrl... urls) {
        return links.getBacklinks(urls);
    }

    public synchronized List<GemtextDocument> getDocumentsByPath(MemexNodeUrl url) {
        return fileSystem.getDocuments(url);
    }
    public synchronized List<MemexImage> getImagesByPath(MemexNodeUrl url) {
        return fileSystem.getImages(url);
    }
    public synchronized List<MemexNodeUrl> getSubdirsByPath(MemexNodeUrl url) {
        return fileSystem.getSubdirs(url);
    }

    public MemexFileSystem getFilesystem() {
        return fileSystem;
    }

    public List<MemexNodeUrl> getDirectories() {
        return fileSystem.getAllDirectories();
    }
    public boolean isDirectory(MemexNodeUrl url) {
        return fileSystem.isDirectory(url);
    }

    public synchronized Set<MemexNodeUrl> deleteImage(MemexNodeUrl url) {
        images.remove(url);
        fileSystem.remove(url);

        Set<MemexNodeUrl> affectedUrls = new HashSet<>();

        affectedUrls.add(url);
        affectedUrls.add(url.getParentUrl());

        return affectedUrls;
    }

    public synchronized Set<MemexNodeUrl> deleteDocument(MemexNodeUrl url) {
        Set<MemexNodeUrl> affectedUrls = new HashSet<>();

        affectedUrls.add(url);
        affectedUrls.add(url.getParentUrl());

        links.getOutlinks(url)
                .stream()
                .map(MemexLink::getDest)
                .forEach(affectedUrls::add);

        documents.remove(url);
        fileSystem.remove(url);

        links.remove(url);

        return affectedUrls;
    }

    public boolean hasTombstone(MemexNodeUrl url) {
        if (tombstones != null && tombstones.getLinkData(url).isPresent())
            return true;
        if (redirects != null && redirects.getLinkData(url).isPresent())
            return true;
        return false;
    }
}
