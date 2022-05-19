package nu.marginalia.wmsa.edge.archive;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.archive.archiver.ArchivedFile;
import nu.marginalia.wmsa.edge.archive.archiver.Archiver;
import nu.marginalia.wmsa.edge.archive.request.EdgeArchiveSubmissionReq;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class EdgeArchiveService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = new GsonBuilder().create();

    private static final Histogram wmsa_archive_store_time = Histogram.build().name("wmsa_archive_store_time").help("-").register();
    private static final Histogram wmsa_archive_fetch_time = Histogram.build().name("wmsa_archive_fetch_time").help("-").register();

    private final Path wikiPath;
    private final Archiver archiver;

    @SneakyThrows
    @Inject
    public EdgeArchiveService(@Named("service-host") String ip,
                              @Named("service-port") Integer port,
                              @Named("wiki-path") Path wikiPath,
                              Archiver archiver,
                              Initialization initialization,
                              MetricsServer metricsServer)
    {
        super(ip, port, initialization, metricsServer);
        this.wikiPath = wikiPath;
        this.archiver = archiver;

        Spark.staticFiles.expireTime(600);

        Spark.post("/page/submit", this::pathPageSubmit);

        Spark.post("/wiki/submit", this::pathWikiSubmit);
        Spark.get("/wiki/has", this::pathWikiHas);
        Spark.get("/wiki/get", this::pathWikiGet);

        Spark.awaitInitialization();
    }

    @SneakyThrows
    private Object pathPageSubmit(Request request, Response response) {
        var timer = wmsa_archive_store_time.startTimer();
        try {
            var body = request.body();
            var data = gson.fromJson(body, EdgeArchiveSubmissionReq.class);

            String domainNamePart = data.getUrl().domain.domain.length() > 32 ? data.getUrl().domain.domain.substring(0, 32) : data.getUrl().domain.domain;
            String fileName = String.format("%s-%10d", domainNamePart, data.getUrl().hashCode());

            archiver.writeData(new ArchivedFile(fileName, body.getBytes()));

            return "ok";
        } finally {
            timer.observeDuration();
        }

    }


    @SneakyThrows
    private Object pathWikiSubmit(Request request, Response response) {
        var timer = wmsa_archive_store_time.startTimer();

        try {
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
        } finally {
            timer.observeDuration();
        }

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
    private String pathWikiGet(Request request, Response response) {
        var timer = wmsa_archive_fetch_time.startTimer();

        try {
            String url = request.queryParams("url");

            var filename = getWikiFilename(wikiPath, url);

            if (Files.exists(filename)) {
                try (var stream = new GZIPInputStream(new FileInputStream(filename.toFile()))) {
                    return new String(stream.readAllBytes());
                }
            } else {
                Spark.halt(404);
                return null;
            }
        }
        finally {
            timer.observeDuration();
        }
    }
}
