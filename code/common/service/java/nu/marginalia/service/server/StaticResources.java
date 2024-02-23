package nu.marginalia.service.server;

import lombok.SneakyThrows;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.resource.ClassPathResource;
import spark.staticfiles.MimeType;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class StaticResources {
    private final long startTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

    @SneakyThrows
    public void serveStatic(String domain, String path, Request req, Response rsp) {
        try {
            if (path.startsWith("..") || domain.startsWith("..")) {
                Spark.halt(403);
            }

            ClassPathResource resource = new ClassPathResource("static/" + domain + "/" + path);
            handleEtagStatic(resource, req, rsp);

            rsp.type(getContentType(path));

            resource.getInputStream().transferTo(rsp.raw().getOutputStream());
        }
        catch (IllegalArgumentException | FileNotFoundException ex) {
            Spark.halt(404);
        }
    }

    private String getContentType(String path) {
        // Opensearch description "must" be served as application/opensearchdescription+xml
        if (path.endsWith("opensearch.xml"))
            return "application/opensearchdescription+xml";

        // Could probably be done with a suffix map instead

        if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".txt")) return "text/plain";
        if (path.endsWith(".xml")) return "application/xml";
        if (path.endsWith(".json")) return "application/json";

        return "application/octet-stream";
    }

    @SneakyThrows
    private void handleEtagStatic(ClassPathResource resource, Request req, Response rsp) {
        rsp.header("Cache-Control", "public,max-age=3600");
        rsp.type(MimeType.fromResource(resource));

        final String etag = staticResourceEtag(resource.getFilename());

        if (etag.equals(req.headers("If-None-Match"))) {
            Spark.halt(304);
        }

        rsp.header("ETag", etag);
    }

    private String staticResourceEtag(String resource) {
        return "\"" + resource.hashCode() + "-" + startTime + "\"";
    }
}
