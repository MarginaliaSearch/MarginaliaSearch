package nu.marginalia.memex.memex.renderer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.memex.memex.MemexData;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import nu.marginalia.memex.memex.system.MemexFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;

public class MemexGmiRenderer {
    private final MemexFileWriter renderedResources;
    private final MemexData data;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public MemexGmiRenderer(@Named("gmi") MemexFileWriter renderedResources,
                            MemexData data)
    {
        this.renderedResources = renderedResources;
        this.data = data;
    }


    public void render(MemexNodeUrl url) {
        if (data.getDocument(url) != null) {
            renderDocument(url);
        }
        else if(data.getImage(url) != null) {
            renderImage(url);
        }
        else if(data.isDirectory(url)) {
            renderIndex(url);
        }
        else if(data.hasTombstone(url)) {
            renderTombstone(url);
        }
        else {
            logger.warn("I don't know how to render {}", url);
        }
    }

    private void renderDocument(MemexNodeUrl url) {
        if ("index.gmi".equals(url.getFilename())) {
            return;
        }

        var doc = data.getDocument(url);
        var renderer = new GemtextRendererFactory().gemtextRendererPublic();

        try {
            renderedResources.write(url, (w) -> {
                doc.render(renderer, w);
                w.println();
                backlinks(w, url);
                w.println("# Navigation\n");
                w.printf("=> %s Back to Index\n", url.getParentUrl());
                w.println("\nReach me at kontakt@marginalia.nu");
            });
        } catch (IOException e) {
            logger.error("Failed to render document " + url, e);
        }

    }

    private void renderImage(MemexNodeUrl url) {
        try {
            renderedResources.write(url, data.getImage(url).realPath);
        } catch (IOException e) {
            logger.error("Failed to image document " + url, e);
        }
    }

    private void renderIndex(MemexNodeUrl url) {

        var renderer = new GemtextRendererFactory().gemtextRendererPublic();

        var doc = data.getDocument(url.child("index.gmi"));
        boolean feed = doc != null && doc.getPragmas().contains("FEED");
        boolean listing = doc != null && doc.getPragmas().contains("LISTING");

        try {
            renderedResources.write(url.child("index.gmi"), (w) -> {

                if (null != doc) doc.render(renderer, w);
                else w.printf("# %s\n", url);


                if (listing) {
                    documentsInUrlListing(url, w);
                }

                if (feed) {
                    w.printf("\n=> %s/feed.gmi Clean gemsub feed\n", url);
                    w.printf("=> %s/feed.xml Atom feed\n", url);
                }

                w.println("\n# Directory Contents\n");
                directoriesInUrl(url, w);
                if (!listing) {
                    documentsInUrl(url, w);
                }
                imagesInUrl(url, w);
                backlinks(w, url, url.child("index.gmi"));
                w.println("\nReach me at kontakt@marginalia.nu");

            });

            if (feed) {
                renderedResources.write(url.child("feed.gmi"), (w) -> {
                    w.printf("# marginalia.nu%s\n", url);
                    w.println();
                    var docs = data.getDocumentsByPath(url);
                    docs.sort(Comparator.comparing(GemtextDocument::getUrl).reversed());
                    for (var d : docs) {
                        if (d.getUrl().getFilename().equals("index.gmi")) {
                            continue;
                        }
                        if (d.getPragmas().contains("DRAFT")) {
                            continue;
                        }
                        w.printf("=> gemini://marginalia.nu%s\t%s %s\n", d.getUrl(), d.getDate(), d.getTitle().replaceAll("\\[[^\\]]+\\]", ""));
                    }
                });
            }
        } catch (IOException e) {
            logger.error("Failed to render document " + url, e);
        }
    }

    private void backlinks(PrintWriter w, MemexNodeUrl... urls) {
        var bls = data.getBacklinks(urls);
        if (!bls.isEmpty()) {
            w.println("\n# Backlinks\n");
            for (var bl : bls) {
                w.printf("=> %s\n", bl.src);
            }
            w.println();
        }
    }

    private void documentsInUrl(MemexNodeUrl url, PrintWriter w) {
        var docs = data.getDocumentsByPath(url);
        if (docs.size() > (data.getDocument(url.child("index.gmi")) == null ? 0 : 1)) {
            for (var d : docs) {
                if (d.getUrl().getFilename().equals("index.gmi")) {
                    continue;
                }
                w.printf("=> %s\t\uD83D\uDDD2 ️️️%s\n", d.getUrl(), d.getTitle());
            }
            w.println();
        }
    }

    private void documentsInUrlListing(MemexNodeUrl url, PrintWriter w) {
        var docs = data.getDocumentsByPath(url);

        docs.sort(Comparator.comparing(GemtextDocument::getUrl).reversed());

        if (!docs.isEmpty()) {
            for (var d : docs) {
                if (d.getUrl().getFilename().equals("index.gmi")) {
                    continue;
                }
                w.printf("=> %s\t%s\n", d.getUrl(), d.getTitle());
            }
            w.println();
        }
    }

    private void imagesInUrl(MemexNodeUrl url, PrintWriter w) {
        var images = data.getImagesByPath(url);
        if (!images.isEmpty()) {
            for (var i : images) {
                w.printf("=> %s \uD83D\uDDBC️ %s\n", i.path, i.path.getFilename());
            }
            w.println();
        }
    }

    private void directoriesInUrl(MemexNodeUrl url, PrintWriter w) {
        var dirs = data.getSubdirsByPath(url);
        final boolean isRoot = url.getParentUrl() == null;
        if (isRoot && dirs.isEmpty()) {
            return;
        }

        if (!isRoot) {
            w.println("=> ../  ⬆ ../ ");

        }
        if (dirs.isEmpty()) {
            w.println();
        }
        for (var d : dirs) {
            w.printf("=> %s \uD83D\uDDC2️ %s/\n", d, d.getFilename());
        }
    }


    private void renderTombstone(MemexNodeUrl url) {
        String message = data.getTombstones().flatMap(tombstones -> tombstones.getLinkData(url)).orElse(null);
        String redir = data.getRedirects().flatMap(redirects -> redirects.getLinkData(url)).orElse(null);

        try {
            renderedResources.write(url, w -> {
                w.printf("# %s is gone\n\n", url);
                if (message != null) {
                    w.printf("%s\n", message);
                }
                if (redir != null) {
                    w.println("Please see");
                    w.printf("=> %s\n", redir);
                }
                backlinks(w, url);
                w.println("\nReach me at kontakt@marginalia.nu");
            });
        } catch (IOException e) {
            logger.error("Failed to render tombstone " + url, e);
        }
    }
}
