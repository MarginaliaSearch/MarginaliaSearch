package nu.marginalia.memex.memex;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.memex.gemini.GeminiService;
import nu.marginalia.memex.gemini.gmi.GemtextDatabase;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.util.dithering.FloydSteinbergDither;
import nu.marginalia.memex.util.dithering.Palettes;
import nu.marginalia.memex.memex.change.GemtextTombstoneUpdateCaclulator;
import nu.marginalia.memex.memex.model.MemexImage;
import nu.marginalia.memex.memex.model.MemexNode;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import nu.marginalia.memex.memex.renderer.MemexRendererers;
import nu.marginalia.memex.memex.system.MemexFileSystemMonitor;
import nu.marginalia.memex.memex.system.MemexFileWriter;
import nu.marginalia.memex.memex.system.git.MemexGitRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Singleton
public class Memex {

    private final MemexData data;
    private final MemexFileSystemMonitor monitor;
    private final MemexGitRepo gitRepo;
    private final MemexLoader loader;

    private final MemexFileWriter resources;
    private final GemtextTombstoneUpdateCaclulator tombstoneUpdateCaclulator;

    private final FloydSteinbergDither ditherer =  new FloydSteinbergDither(Palettes.MARGINALIA_PALETTE, 640, 480);
    private final MemexRendererers renderers;

    private static final Logger logger = LoggerFactory.getLogger(Memex.class);

    @Inject
    public Memex(MemexData data,
                 @Nullable MemexFileSystemMonitor monitor,
                 MemexGitRepo gitRepo, MemexLoader loader,
                 @Named("html") MemexFileWriter htmlFiles,
                 GemtextTombstoneUpdateCaclulator tombstoneUpdateCaclulator,
                 MemexRendererers renderers,
                 GeminiService geminiService) {
        this.data = data;
        this.monitor = monitor;
        this.gitRepo = gitRepo;
        this.loader = loader;
        this.resources = htmlFiles;
        this.tombstoneUpdateCaclulator = tombstoneUpdateCaclulator;
        this.renderers = renderers;

        Schedulers.io().scheduleDirect(this::load);
        if (monitor != null) {
            Schedulers.io().schedulePeriodicallyDirect(this::refreshUpdatedUrls, 1, 1, TimeUnit.SECONDS);
        }

        Schedulers.newThread().scheduleDirect(geminiService::run);
    }

    private void refreshUpdatedUrls() {
        var updatedUrls = monitor.getUpdatedUrls();
        for (var url : updatedUrls) {
            try {
                if (url.toString().endsWith(".gmi")) {
                    var updates = loader.reloadNode(url);
                    updates.forEach(renderers::render);

                    if (!updates.isEmpty()) {
                        renderers.render(url.getParentUrl());
                    }
                } else if (url.toString().endsWith(".png")) {
                    var updates = loader.reloadImage(url);
                    renderers.render(url);

                    if (!updates.isEmpty()) {
                        renderers.render(url.getParentUrl());
                    }
                }

                if (tombstoneUpdateCaclulator.isTombstoneFile(url)) {
                    loader.loadTombstones().forEach(renderers::render);
                }
                if (tombstoneUpdateCaclulator.isRedirectFile(url)) {
                    loader.loadRedirects().forEach(renderers::render);
                }
            }
            catch (Exception ex) {
                logger.error("Failed to refresh URL " + url, ex);
            }
        }
    }

    private void load() {
        copyStylesheet();

        try {
            loader.load();
            renderAll();
        }
        catch (IOException ex) {
            logger.error("Failed to load", ex);
        }
    }

    private void copyStylesheet() {
        try (var resource = Objects.requireNonNull(
                ClassLoader.getSystemResourceAsStream("static/memex/style-new.css"), "Could not load stylesheet")) {
            resources.write(new MemexNodeUrl("/style-new.css"), resource.readAllBytes());
        }
        catch (Exception ex) {
            logger.error("Failed to copy stylesheet", ex);
        }

        try (var resource = Objects.requireNonNull(
                ClassLoader.getSystemResourceAsStream("static/memex/ico/dir.png"), "Could not copy file")) {
            resources.write(new MemexNodeUrl("/ico/dir.png"), resource.readAllBytes());
        }
        catch (Exception ex) {
            logger.error("Failed to copy file", ex);
        }


        try (var resource = Objects.requireNonNull(
                ClassLoader.getSystemResourceAsStream("static/memex/ico/file.png"), "Could not copy file")) {
            resources.write(new MemexNodeUrl("/ico/file.png"), resource.readAllBytes());
        }
        catch (Exception ex) {
            logger.error("Failed to copy file", ex);
        }


        try (var resource = Objects.requireNonNull(
                ClassLoader.getSystemResourceAsStream("static/memex/ico/root.png"), "Could not copy file")) {
            resources.write(new MemexNodeUrl("/ico/root.png"), resource.readAllBytes());
        }
        catch (Exception ex) {
            logger.error("Failed to copy file", ex);
        }

        try (var resource = Objects.requireNonNull(
                ClassLoader.getSystemResourceAsStream("static/memex/ico/pic16.png"), "Could not copy file")) {
            resources.write(new MemexNodeUrl("/ico/pic16.png"), resource.readAllBytes());
        }
        catch (Exception ex) {
            logger.error("Failed to copy file", ex);
        }
    }

    private void renderAll() {
        data.forEach((url, doc) -> {
            renderers.render(url);
        });
        data.getDirectories().forEach(renderers::render);
        data.getImages().forEach(img -> renderers.render(img.path));

        data.getTombstones().ifPresent(this::renderTombstoneFromGemtextDb);
        data.getRedirects().ifPresent(this::renderTombstoneFromGemtextDb);
    }


    private void renderTombstoneFromGemtextDb(GemtextDatabase db) {
        db.keys()
                .stream()
                .map(MemexNodeUrl::new)
                .filter(url -> getDocument(url) == null)
                .forEach(renderers::render);
    }

    public void updateNode(MemexNodeUrl node, String text) throws IOException {
        var nodes = loader.updateNode(node, text);

        nodes.forEach(renderers::render);

        renderers.render(node.getParentUrl());
    }

    public GemtextDocument getDocument(MemexNodeUrl url) {
        return data.getDocument(url);
    }
    public MemexImage getImage(MemexNodeUrl url) {
        return data.getImage(url);
    }


    public void createNode(MemexNodeUrl node, String text) throws IOException {
        var nodes = loader.createNode(node, text);

        nodes.forEach(renderers::render);

        renderers.render(node.getParentUrl());
    }


    public void uploadImage(MemexNodeUrl url, byte[] bytes) throws IOException {

        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        var convertedImage = ditherer.convert(image);
        var baosOut = new ByteArrayOutputStream();
        ImageIO.write(convertedImage, "png", baosOut);

        loader.uploadImage(url, baosOut.toByteArray());

        renderers.render(url);
        renderers.render(url.getParentUrl());
    }

    public void delete(MemexNode node, String message) throws IOException {
        tombstoneUpdateCaclulator.addTombstone(node.getUrl(), message)
                .visit(this);
        loader.loadTombstones();
        loader.delete(node).forEach(renderers::render);
    }

    public List<GemtextDocument> getDocumentsByPath(MemexNodeUrl url) {
        return data.getDocumentsByPath(url);
    }

    public void gitPull() {
        gitRepo.pull();
    }

    public void rename(MemexNode src, MemexNodeUrl dst) throws IOException {
        tombstoneUpdateCaclulator.addRedirect(src.getUrl(), dst.toString())
                        .visit(this);
        loader.loadRedirects();
        loader.rename(src, dst).forEach(renderers::render);
    }

    public byte[] getRaw(MemexNodeUrl url) throws IOException {
        return loader.getRaw(url);
    }
}
