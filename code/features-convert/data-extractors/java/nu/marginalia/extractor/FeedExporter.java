package nu.marginalia.extractor;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.io.crawldata.CrawledDomainReader;
import nu.marginalia.io.crawldata.SerializableCrawlDataStream;
import nu.marginalia.link_parser.FeedExtractor;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.jsoup.Jsoup;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class FeedExporter implements ExporterIf {
    private final FileStorageService storageService;


    @Inject
    public FeedExporter(FileStorageService storageService) {
        this.storageService = storageService;
    }

    public void export(FileStorageId crawlId, FileStorageId destId) throws Exception {
        FileStorage destStorage = storageService.getStorage(destId);

        var tmpFile = Files.createTempFile(destStorage.asPath(), "feeds", ".csv.gz",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

        Path inputDir = storageService.getStorage(crawlId).asPath();

        try (var bw = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))))
        {
            Path crawlerLogFile = inputDir.resolve("crawler.log");

            var tagWriter = new FeedCsvWriter(bw);

            for (var item : WorkLog.iterable(crawlerLogFile)) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                Path crawlDataPath = inputDir.resolve(item.relPath());
                try (var stream = CrawledDomainReader.createDataStream(crawlDataPath)) {
                    exportFeeds(tagWriter, stream);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            Files.move(tmpFile, destStorage.asPath().resolve("feeds.csv.gz"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        finally {
            Files.deleteIfExists(tmpFile);
        }

    }

    private boolean exportFeeds(FeedCsvWriter exporter, SerializableCrawlDataStream stream) throws IOException, URISyntaxException {
        FeedExtractor feedExtractor = new FeedExtractor(new LinkParser());

        int size = stream.sizeHint();

        while (stream.hasNext()) {
            if (!(stream.next() instanceof CrawledDocument doc))
                continue;
            if (null == doc.documentBody)
                continue;

            var baseUrl = new EdgeUrl(doc.url);
            var parsed = Jsoup.parse(doc.documentBody);

            List<EdgeUrl> feedUrls = new ArrayList<>();
            for (var link : parsed.select("link[rel=alternate]")) {
                feedExtractor
                        .getFeedFromAlternateTag(baseUrl, link)
                        .ifPresent(feedUrls::add);
            }

            // Take the shortest path if there are multiple
            if (!feedUrls.isEmpty()) {
                feedUrls.sort(Comparator.comparing(url -> url.path.length()));
                exporter.accept(baseUrl.domain, size, feedUrls.getFirst());
            }

            // Only consider the first viable document, otherwise this will be very slow
            break;
        }

        return true;
    }

    private static class FeedCsvWriter {
        private final BufferedWriter writer;

        private FeedCsvWriter(BufferedWriter writer) {
            this.writer = writer;
        }

        @SneakyThrows
        public void accept(EdgeDomain domain, int size, EdgeUrl path) {
            writer.write(String.format("\"%s\",\"%s\",\"%s\"\n",
                    csvify(domain),
                    csvify(size),
                    csvify(path)));
        }

        private static String csvify(Object field) {
            return field.toString().replace("\"", "\"\"");
        }
    }

}
