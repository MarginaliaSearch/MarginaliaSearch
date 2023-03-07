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
            ClassPathResource resource = new ClassPathResource("static/" + domain + "/" + path);
            handleEtagStatic(resource, req, rsp);
            resource.getInputStream().transferTo(rsp.raw().getOutputStream());
        }
        catch (IllegalArgumentException | FileNotFoundException ex) {
            Spark.halt(404);
        }
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
