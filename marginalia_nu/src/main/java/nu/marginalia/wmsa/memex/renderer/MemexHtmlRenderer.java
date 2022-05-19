package nu.marginalia.wmsa.memex.renderer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.wmsa.memex.MemexData;
import nu.marginalia.wmsa.memex.model.*;
import nu.marginalia.wmsa.memex.model.render.*;
import nu.marginalia.wmsa.memex.system.MemexFileWriter;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MemexHtmlRenderer {

    private final MemexFileWriter htmlRenderedResources;
    private final MemexFileWriter gmiRenderedResources;

    private final MemexData data;

    private final MustacheRenderer<MemexRendererViewModel> viewRenderer;
    private final MustacheRenderer<MemexRendererIndexModel> indexRenderer;
    private final MustacheRenderer<MemexRendererIndexModel> indexFeedRenderer;
    private final MustacheRenderer<MemexRendererImageModel> imageRenderer;
    private final MustacheRenderer<MemexRendererTombstoneModel> tombstoneRenderer;

    private final MustacheRenderer<MemexRenderUpdateFormModel> updateFormRenderer;
    private final MustacheRenderer<MemexRenderUploadFormModel> uploadFormRenderer;
    private final MustacheRenderer<MemexRenderCreateFormModel> createFormRenderer;
    private final MustacheRenderer<MemexRendererDeleteFormModel> deleteFormRenderer;
    private final MustacheRenderer<MemexRendererRenameFormModel> renameFormRenderer;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public MemexHtmlRenderer(
            @Named("html") MemexFileWriter htmlRenderedResources,
            @Named("gmi") MemexFileWriter gmiRenderedResources,
            MemexData data) throws IOException {
        this.htmlRenderedResources = htmlRenderedResources;
        this.gmiRenderedResources = gmiRenderedResources;
        this.data = data;

        final var rendererFactory = new RendererFactory();

        viewRenderer = rendererFactory.renderer("memex/memex-view");
        indexRenderer = rendererFactory.renderer("memex/memex-index");
        indexFeedRenderer = rendererFactory.renderer("memex/memex-index-feed");
        imageRenderer = rendererFactory.renderer("memex/memex-image");

        tombstoneRenderer = rendererFactory.renderer("memex/memex-tombstone");

        updateFormRenderer = rendererFactory.renderer("memex/memex-update-form");
        uploadFormRenderer = rendererFactory.renderer("memex/memex-upload-form");
        deleteFormRenderer = rendererFactory.renderer("memex/memex-delete-form");
        renameFormRenderer = rendererFactory.renderer("memex/memex-rename-form");
        createFormRenderer = rendererFactory.renderer("memex/memex-create-form");

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

    public void renderDocument(MemexNodeUrl url) {
        var doc = Objects.requireNonNull(data.getDocument(url), "could not get document " + url);
        var htmlRenderer = new GemtextRendererFactory("", url.toString()).htmlRendererEditable();
        var model = new MemexRendererViewModel(doc,
                doc.getTitle(),
                data.getBacklinks(url),
                doc.render(htmlRenderer),
                url.getParentStr()
        );

        try {
            htmlRenderedResources.write(url, viewRenderer.render(model, Map.of("urlRoot", "")));
        } catch (IOException e) {
            logger.error("Failed to render document " + url, e);
        }

    }

    public void renderIndex(MemexNodeUrl url) {

        var docs = data.getDocumentsByPath(url);
        var images = data.getImagesByPath(url);
        var dirs = data.getSubdirsByPath(url);

        var tasks = docs.stream().flatMap(doc -> doc.getOpenTopTasks().entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MemexIndexTask(entry.getValue().getLeft(),
                        entry.getKey().toString(),
                        doc.getUrl().toString(),
                        entry.getValue().getRight().style))
        ).collect(Collectors.toList());

        List<MemexLink> backlinks = data.getBacklinks(url, url.child("index.gmi"));
        var model = new MemexRendererIndexModel(url, docs, images, new ArrayList<>(dirs), tasks, backlinks);

        try {
            htmlRenderedResources.write(url.child("index.html"), indexRenderer.render(model, Map.of("urlRoot", "")));
            if (model.hasPragma("FEED")) {
                String nowStr = OffsetDateTime.now().with(ChronoField.MILLI_OF_SECOND, 0).format(DateTimeFormatter.ISO_DATE_TIME);
                htmlRenderedResources.write(url.child("feed.xml"), indexFeedRenderer.render(model,
                        Map.of("domain", "https://memex.marginalia.nu", "now", nowStr)));
                gmiRenderedResources.write(url.child("feed.xml"), indexFeedRenderer.render(model,
                        Map.of("domain", "gemini://marginalia.nu", "now", nowStr)));
            }
        } catch (IOException e) {
            logger.error("Failed to render index model " + url, e);
        }
    }

    public void renderImage(MemexNodeUrl url) {
        var img = data.getImage(url);
        var backlinks = data.getBacklinks(img.path);
        var parent = img.path.getParentStr();
        var model = new MemexRendererImageModel(img, backlinks, parent);

        try {
            htmlRenderedResources.write(img.path, imageRenderer.render(model, Map.of("urlRoot", "")));
        } catch (IOException e) {
            logger.error("Failed to render image model " + img.path, e);
        }
    }

    public void renderTombstone(MemexNodeUrl url) {

        String message = data.getTombstones().flatMap(tombstones -> tombstones.getLinkData(url)).orElse(null);
        String redir = data.getRedirects().flatMap(redirects -> redirects.getLinkData(url)).orElse(null);

        var model =  new MemexRendererTombstoneModel(url,
                message,
                redir,
                data.getBacklinks(url));

        try {
            htmlRenderedResources.write(url, tombstoneRenderer.render(model, Map.of("urlRoot", "")));
        } catch (IOException e) {
            logger.error("Failed to render tombstone model " + url, e);
        }
    }

    @SneakyThrows
    public String renderModel(MemexRendererDeleteFormModel model) {
        return deleteFormRenderer.render(model, Map.of("urlRoot", ""));
    }


    @SneakyThrows
    public String renderModel(MemexRendererRenameFormModel model) {
        return renameFormRenderer.render(model, Map.of("urlRoot", ""));
    }

    @SneakyThrows
    public String renderModel(MemexRenderCreateFormModel model) {
        return createFormRenderer.render(model, Map.of("urlRoot", ""));
    }

    @SneakyThrows
    public String renderModel(MemexRenderUploadFormModel model) {
        return uploadFormRenderer.render(model, Map.of("urlRoot", ""));
    }

    @SneakyThrows
    public String renderModel(MemexRenderUpdateFormModel model) {
        return updateFormRenderer.render(model, Map.of("urlRoot", ""));
    }

}
