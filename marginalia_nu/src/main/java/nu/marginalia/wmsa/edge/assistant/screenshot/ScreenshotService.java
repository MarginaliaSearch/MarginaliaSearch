package nu.marginalia.wmsa.edge.assistant.screenshot;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import spark.Request;
import spark.Response;
import spark.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import static java.lang.Integer.parseInt;

public class ScreenshotService {

    private final Path screenshotsRoot =  Path.of("/var/lib/wmsa/archive/screenshots/screenshots/");
    private final Path screenshotsRootWebp =  Path.of("/var/lib/wmsa/archive.fast/screenshots/");
    private final EdgeDataStoreDao edgeDataStoreDao;
    private final long MIN_FILE_SIZE = 4096;

    @Inject
    public ScreenshotService(EdgeDataStoreDao edgeDataStoreDao) {
        this.edgeDataStoreDao = edgeDataStoreDao;
    }

    public boolean hasScreenshot(EdgeId<EdgeDomain> domainId) {
        EdgeDomain domain = edgeDataStoreDao.getDomain(domainId);

        Path p = getScreenshotPath(screenshotsRootWebp, domain, ".webp");
        if (p == null) {
            p = getScreenshotPath(screenshotsRoot, domain, ".png");
        }

        try {
            return p != null && Files.size(p) >= MIN_FILE_SIZE;
        } catch (IOException e) {
            return false;
        }
    }

    @SneakyThrows
    public Object serveScreenshotRequest(Request request, Response response) {
        if (Strings.isNullOrEmpty(request.params("id"))) {
            response.redirect("https://search.marginalia.nu/");
            return null;
        }

        int id = parseInt(request.params("id"));

        Path p = null;
        if (id == 0) {
            p = screenshotsRootWebp.resolve("dummy-snapshot.webp");
        } else {
            EdgeDomain domain;
            try {
                domain = edgeDataStoreDao.getDomain(new EdgeId<>(id));
                p = getScreenshotPath(screenshotsRootWebp, domain, ".webp");
                if (p == null) {
                    p = getScreenshotPath(screenshotsRoot, domain, ".png");
                }

                if (p != null && Files.size(p) <= MIN_FILE_SIZE) {
                    p = null;
                }
            } catch (NoSuchElementException ex) {
                domain = new EdgeDomain("error.example.com");
            }

            if (p == null) {
                response.type("image/svg+xml");

                return String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                        "<svg\n" +
                        "   xmlns=\"http://www.w3.org/2000/svg\"\n" +
                        "   width=\"640px\"\n" +
                        "   height=\"480px\"\n" +
                        "   viewBox=\"0 0 640 480\"\n" +
                        "   version=\"1.1\">\n" +
                        "  <g>\n" +
                        "    <rect\n" +
                        "       style=\"fill:#808080\"\n" +
                        "       id=\"rect288\"\n" +
                        "       width=\"595.41992\"\n" +
                        "       height=\"430.01825\"\n" +
                        "       x=\"23.034981\"\n" +
                        "       y=\"27.850344\" />\n" +
                        "    <text\n" +
                        "       xml:space=\"preserve\"\n" +
                        "       style=\"font-size:100px;fill:#909090;font-family:sans-serif;\"\n" +
                        "       x=\"20\"\n" +
                        "       y=\"120\">Placeholder</text>\n" +
                        "    <text\n" +
                        "       xml:space=\"preserve\"\n" +
                        "       style=\"font-size:32px;fill:#000000;font-family:monospace;\"\n" +
                        "       x=\"320\" y=\"240\" dominant-baseline=\"middle\" text-anchor=\"middle\">%s</text>\n" +
                        "  </g>\n" +
                        "</svg>\n", domain);
            }
        }
        response.status(200);
        response.header("Cache-control", "public,max-age=3600");
        if (p.toString().endsWith("webp")) {
            response.type("image/webp");
        } else {
            response.type("image/png");
        }
        IOUtils.copy(new ByteArrayInputStream(Files.readAllBytes(p)), response.raw().getOutputStream());
        return "";
    }

    private Path getScreenshotPath(Path root, EdgeDomain domain, String ending) {

        var p = root.resolve(domain.toString() + ending);
        if (!p.normalize().startsWith(root)) {
            return null;
        }

        if (!Files.exists(p)) {
            return null;
        }

        return p;
    }

}
