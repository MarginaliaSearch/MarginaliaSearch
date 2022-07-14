package nu.marginalia.wmsa.memex;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.gemini.gmi.GemtextDocument;
import nu.marginalia.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.wmsa.auth.client.AuthClient;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.memex.change.GemtextMutation;
import nu.marginalia.wmsa.memex.change.update.GemtextDocumentUpdateCalculator;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import nu.marginalia.wmsa.memex.model.render.*;
import nu.marginalia.wmsa.memex.renderer.MemexHtmlRenderer;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import javax.servlet.MultipartConfigElement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static spark.Spark.*;

public class MemexService extends Service {
    private final GemtextDocumentUpdateCalculator updateCalculator;
    private final Memex memex;
    private final MemexHtmlRenderer renderer;
    private final AuthClient authClient;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public MemexService(@Named("service-host") String ip,
                        @Named("service-port") Integer port,
                        GemtextDocumentUpdateCalculator updateCalculator,
                        Memex memex,
                        MemexHtmlRenderer renderer,
                        AuthClient authClient,
                        Initialization initialization,
                        MetricsServer metricsServer,
                        @Named("memex-html-resources") Path memexHtmlDir
                        ) {

        super(ip, port, initialization, metricsServer, () -> {
            staticFiles.externalLocation(memexHtmlDir.toString());
            staticFiles.disableMimeTypeGuessing();
            staticFiles.registerMimeType("gmi", "text/html");
            staticFiles.registerMimeType("png", "text/html");
            staticFiles.expireTime(60);
            staticFiles.header("Cache-control", "public,proxy-revalidate");
        });

        this.updateCalculator = updateCalculator;
        this.memex = memex;
        this.renderer = renderer;
        this.authClient = authClient;

        Spark.get("git-pull", this::gitPull);

        Spark.path("public/api", () -> {
            before((req, rsp) -> {
                logger.info("{} {}", req.requestMethod(), req.pathInfo());
            });
            after((req, rsp) -> {
                rsp.header("Cache-control", "no-cache");
            });

            post("/create", this::create);
            get("/create", this::createForm, this::renderModel);
            post("/upload", this::upload);
            get("/upload", this::uploadForm, this::renderModel);
            post("/update", this::update);
            get("/update", this::updateForm, this::renderModel);
            post("/rename", this::rename);
            get("/rename", this::renameForm, this::renderModel);
            post("/delete", this::delete);
            get("/delete", this::deleteForm, this::renderModel);

            get("/raw", this::raw);
        });
    }

    private Object raw(Request request, Response response) throws IOException {
        final MemexNodeUrl url = new MemexNodeUrl(Objects.requireNonNull(request.queryParams("url")));

        response.type(url.toNode().getType().mime);
        response.header("Content-Disposition", "attachment; filename=" + url.getFilename());
        response.raw().getOutputStream().write(memex.getRaw(url));

        return "";
    }

    private Object renameForm(Request request, Response response) {
        final String type = Objects.requireNonNull(request.queryParams("type"));
        final MemexNodeUrl url = new MemexNodeUrl(Objects.requireNonNull(request.queryParams("url")));

        authClient.redirectToLoginIfUnauthenticated("MEMEX", request, response);

        if ("gmi".equals(type)) {
            var doc = memex.getDocument(url);
            if (null == doc) {
                Spark.halt(404);
            }

            final String docHtml = doc.render(new GemtextRendererFactory("", url.toString()).htmlRendererEditable());
            return new MemexRendererRenameFormModel(docHtml,
                    null, url, "gmi");
        }
        else if ("img".equals(type)) {
            var img = memex.getImage(url);
            if (null == img) {
                Spark.halt(404);
            }
            return new MemexRendererRenameFormModel(null,
                    new MemexRendererImageModel(img, Collections.emptyList(), null),
                    url, "img");
        }

        Spark.halt(HttpStatus.SC_BAD_REQUEST);
        return null;
    }

    private Object rename(Request request, Response response) throws IOException {
        authClient.redirectToLoginIfUnauthenticated("MEMEX", request, response);

        var url = Objects.requireNonNull(request.queryParams("url"));
        var name = Objects.requireNonNull(request.queryParams("name"));
        var type = Objects.requireNonNull(request.queryParams("type"));
        var confirm = Objects.requireNonNull(request.queryParams("confirm"));

        if (!"on".equals(confirm)) {
            logger.error("Confirm dialog not checked, was {}", confirm);
            Spark.halt(HttpStatus.SC_BAD_REQUEST, "Confirm was not checked");
        }

        memex.rename(new MemexNodeUrl(url).toNode(), new MemexNodeUrl(name));

        response.redirect("https://memex.marginalia.nu/"+name);
        return null;

    }

    private Object gitPull(Request request, Response response) {
        logger.info("Git pull by request");
        memex.gitPull();
        return "Ok";
    }

    private String renderModel(Object model) {
        return ((MemexRendererableDirect)model).render(renderer);
    }

    private MemexRendererDeleteFormModel deleteForm(Request request, Response response) {
        final String type = Objects.requireNonNull(request.queryParams("type"));
        final MemexNodeUrl url = new MemexNodeUrl(Objects.requireNonNull(request.queryParams("url")));

        authClient.redirectToLoginIfUnauthenticated("MEMEX", request, response);

        if ("gmi".equals(type)) {
            var doc = memex.getDocument(url);
            if (null == doc) {
                Spark.halt(404);
            }

            final String docHtml = doc.render(new GemtextRendererFactory("", url.toString()).htmlRendererEditable());
            return new MemexRendererDeleteFormModel(docHtml,
                    null, url, "gmi");
        }
        else if ("img".equals(type)) {
            var img = memex.getImage(url);
            if (null == img) {
                Spark.halt(404);
            }
            return new MemexRendererDeleteFormModel(null,
                    new MemexRendererImageModel(img, Collections.emptyList(), null),
                    url, "img");
        }

        Spark.halt(HttpStatus.SC_BAD_REQUEST);
        return null;
    }

    private Object delete(Request request, Response response) throws IOException {
        authClient.requireLogIn(Context.fromRequest(request));

        var url = Objects.requireNonNull(request.queryParams("url"));
        var message = Objects.requireNonNull(request.queryParams("note"));
        var type = Objects.requireNonNull(request.queryParams("type"));
        var confirm = Objects.requireNonNull(request.queryParams("confirm"));

        if (!"on".equals(confirm)) {
            logger.error("Confirm dialog not checked, was {}", confirm);
            Spark.halt(HttpStatus.SC_BAD_REQUEST, "Confirm was not checked");
        }

        memex.delete(new MemexNodeUrl(url).toNode(), message);

        response.redirect("https://memex.marginalia.nu/"+url);
        return null;
    }

    private Object update(Request request, Response response) throws IOException {
        authClient.requireLogIn(Context.fromRequest(request));

        String extUrl = Objects.requireNonNull(request.queryParams("url"));
        String extSection = Objects.requireNonNull(request.queryParams("section"));
        String newSectionText = Objects.requireNonNull(request.queryParams("text"));

        var url = new MemexNodeUrl(extUrl);
        var section = MemexNodeHeadingId.parse(extSection);
        var lines = Arrays.asList(newSectionText.split("\r?\n")).toArray(String[]:: new);

        var sectionGemtext = new GemtextDocument(url, lines, section);
        var updates = updateCalculator.calculateUpdates(memex.getDocument(url), section, sectionGemtext);

        for (GemtextMutation mutation : updates) {
            mutation.visit(memex);
        }

        response.redirect("https://memex.marginalia.nu/"+extUrl);
        return "";
    }

    private Object create(Request request, Response response) throws IOException {
        authClient.requireLogIn(Context.fromRequest(request));

        String directory = Objects.requireNonNull(request.queryParams("directory"));
        String filename = Objects.requireNonNull(request.queryParams("filename"));
        String text = Objects.requireNonNull(request.queryParams("text"));
        var url = new MemexNodeUrl(Path.of(directory).resolve(filename).toString());

        memex.createNode(url, text);

        response.redirect("https://memex.marginalia.nu/"+directory + "/" + filename);
        return "";
    }

    private Object createForm(Request request, Response response) {
        final MemexNodeUrl url = new MemexNodeUrl(Objects.requireNonNull(request.queryParams("url")));
        authClient.redirectToLoginIfUnauthenticated("MEMEX", request, response);

        return new MemexRenderCreateFormModel(url, memex.getDocumentsByPath(url));
    }

    private Object uploadForm(Request request, Response response) {
        final MemexNodeUrl url = new MemexNodeUrl(Objects.requireNonNull(request.queryParams("url")));
        authClient.redirectToLoginIfUnauthenticated("MEMEX", request, response);

        return new MemexRenderUploadFormModel(url, memex.getDocumentsByPath(url));
    }

    private Object updateForm(Request request, Response response) {
        final MemexNodeUrl url = new MemexNodeUrl(Objects.requireNonNull(request.queryParams("url")));
        authClient.redirectToLoginIfUnauthenticated("MEMEX", request, response);

        var doc = memex.getDocument(url);

        return new MemexRenderUpdateFormModel(url, doc.getTitle(), "0", doc.getSectionGemtext(MemexNodeHeadingId.ROOT));
    }


    @SneakyThrows
    private Object upload(Request request, Response response) {
        authClient.requireLogIn(Context.fromRequest(request));

        request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp", 50*1024*1024, 50*1024*1024, 25*1024*1024));

        String directory = Objects.requireNonNull(request.queryParams("directory"));
        String filename = Objects.requireNonNull(request.queryParams("filename"));
        var url = new MemexNodeUrl(Path.of(directory).resolve(filename).toString());
        try (InputStream input = request.raw().getPart("file").getInputStream()) {
            byte[] data = input.readAllBytes();
            memex.uploadImage(url, data);
        }

        response.redirect("https://memex.marginalia.nu/"+directory + "/" + filename);
        return "";
    }

}
