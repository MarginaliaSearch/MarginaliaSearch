package nu.marginalia.wmsa.encyclopedia;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class EncyclopediaService extends Service {

    private static final Logger logger = LoggerFactory.getLogger(EncyclopediaService.class);
    private final MustacheRenderer<String> wikiErrorPageRenderer;
    private final MustacheRenderer<Object> wikiSearchResultRenderer;
    private Path wikiPath;
    private EncyclopediaDao encyclopediaDao;

    @Inject
    public EncyclopediaService(@Named("service-host") String ip,
                               @Named("service-port") Integer port,
                               @Named("wiki-path") Path wikiPath,
                               EncyclopediaDao encyclopediaDao,
                               RendererFactory rendererFactory,
                               Initialization initialization,
                               MetricsServer metricsServer)
            throws IOException {
        super(ip, port, initialization, metricsServer);
        this.wikiPath = wikiPath;
        this.encyclopediaDao = encyclopediaDao;

        if (rendererFactory != null) {
            wikiErrorPageRenderer = rendererFactory.renderer("encyclopedia/wiki-error");
            wikiSearchResultRenderer = rendererFactory.renderer("encyclopedia/wiki-search");
        }
        else {
            wikiErrorPageRenderer = null;
            wikiSearchResultRenderer = null;
        }


        Spark.get("/public/wiki/*", this::getWikiPage);
        Spark.get("/public/wiki-search", this::searchWikiPage);

        Spark.get("/wiki/has", this::pathWikiHas);
        Spark.post("/wiki/submit", this::pathWikiSubmit);
    }


    @SneakyThrows
    private Object getWikiPage(Request req, Response rsp) {
        final String[] splats = req.splat();
        if (splats.length == 0)
            rsp.redirect("https://encyclopedia.marginalia.nu/wiki-start.html");


        final String name = splats[0];

        String pageName = encyclopediaDao.resolveEncylopediaRedirect(name).orElse(name);

        logger.info("Resolved {} -> {}", name, pageName);

        return wikiGet(pageName)
                .or(() -> resolveWikiPageNameWrongCase(name))
                .orElseGet(() -> renderSearchPage(name));
    }

    private Optional<String> resolveWikiPageNameWrongCase(String name) {
        var rsp = encyclopediaDao.findEncyclopediaPageDirect(name);

        if (rsp.isEmpty()) {
            return Optional.of(renderSearchPage(name));
        }

        name = rsp.get().getInternalName();
        return wikiGet(name);
    }

    private String renderSearchPage(String s) {
        return wikiSearchResultRenderer.render(
                Map.of("query", s,
                        "error", "true",
                        "results", encyclopediaDao.findEncyclopediaPages(s)));
    }

    @SneakyThrows
    private Object searchWikiPage(Request req, Response rsp) {
        final var ctx = Context.fromRequest(req);

        String term = req.queryParams("query");
        if (null == term) {
            rsp.redirect("https://encyclopedia.marginalia.nu/wiki-start.html");
            return "";
        }

        return wikiSearchResultRenderer.render(
                Map.of("query", term,
                        "results",
                        encyclopediaDao.findEncyclopediaPages(term))
        );
    }



    private Path getWikiFilename(Path base, String url) {
        Path p = base;

        int urlHash = url.hashCode();

        p = p.resolve(Integer.toString(urlHash & 0xFF));
        p = p.resolve(Integer.toString((urlHash>>>8) & 0xFF));
        p = p.resolve(Integer.toString((urlHash>>>16) & 0xFF));
        p = p.resolve(Integer.toString((urlHash>>>24) & 0xFF));

        String fileName = url.chars()
                .mapToObj(this::encodeUrlChar)
                .collect(Collectors.joining());

        if (fileName.length() > 128) {
            fileName = fileName.substring(0, 128) + (((long)urlHash)&0xFFFFFFFFL);
        }

        return p.resolve(fileName + ".gz");
    }


    private String encodeUrlChar(int i) {
        if (i >= 'a' && i <= 'z') {
            return Character.toString(i);
        }
        if (i >= 'A' && i <= 'Z') {
            return Character.toString(i);
        }
        if (i >= '0' && i <= '9') {
            return Character.toString(i);
        }
        if (i == '.') {
            return Character.toString(i);
        }
        else {
            return String.format("%%%2X", i);
        }
    }

    @SneakyThrows
    private Object pathWikiHas(Request request, Response response) {
        return Files.exists(getWikiFilename(wikiPath, request.queryParams("url")));
    }


    @SneakyThrows
    private Optional<String> wikiGet(String name) {

        var filename = getWikiFilename(wikiPath, name);

        if (Files.exists(filename)) {
            try (var stream = new GZIPInputStream(new FileInputStream(filename.toFile()))) {
                return Optional.of(new String(stream.readAllBytes()));
            }
        } else {
            return Optional.empty();
        }
    }


    @SneakyThrows
    private Object pathWikiSubmit(Request request, Response response) {
        byte[] data = request.bodyAsBytes();

        String wikiUrl = request.queryParams("url");
        Path filename = getWikiFilename(wikiPath, wikiUrl);

        Files.createDirectories(filename.getParent());

        System.out.println(new String(data));
        logger.debug("Writing {} to {}", wikiUrl, filename);

        try (var gos = new GZIPOutputStream(new FileOutputStream(filename.toFile()))) {
            gos.write(data);
            gos.flush();
        }

        return "ok";

    }
}
