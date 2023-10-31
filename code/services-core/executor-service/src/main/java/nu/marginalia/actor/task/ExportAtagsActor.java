package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.set.hash.TLongHashSet;
import lombok.SneakyThrows;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.storage.model.*;
import org.jsoup.Jsoup;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

@Singleton
public class ExportAtagsActor extends RecordActorPrototype {
    private static final LinkParser linkParser = new LinkParser();
    private static final MurmurHash3_128 hash = new MurmurHash3_128();
    private final FileStorageService storageService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Export(FileStorageId crawlId) implements ActorStep {}
    public record Run(FileStorageId crawlId, FileStorageId destId) implements ActorStep {}
    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(FileStorageId crawlId) -> {
                var storageBase = storageService.getStorageBase(FileStorageBaseType.STORAGE);
                var storage = storageService.allocateTemporaryStorage(storageBase, FileStorageType.EXPORT, "atag-export", "Anchor Tags " + LocalDateTime.now());

                if (storage == null) yield new Error("Bad storage id");
                yield new Run(crawlId, storage.id());
            }
            case Run(FileStorageId crawlId, FileStorageId destId) -> {
                FileStorage destStorage = storageService.getStorage(destId);
                storageService.setFileStorageState(destId, FileStorageState.NEW);

                var tmpFile = Files.createTempFile(destStorage.asPath(), "atags", ".csv.gz",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

                Path inputDir = storageService.getStorage(crawlId).asPath();

                var reader = new CrawledDomainReader();

                try (var bw = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))));
                )
                {
                    Path crawlerLogFile = inputDir.resolve("crawler.log");

                    var tagWriter = new ATagCsvWriter(bw);

                    for (var item : WorkLog.iterable(crawlerLogFile)) {
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }

                        Path crawlDataPath = inputDir.resolve(item.relPath());
                        try (var stream = reader.createDataStream(crawlDataPath)) {
                            exportLinks(tagWriter, stream);
                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    Files.move(tmpFile, destStorage.asPath().resolve("atags.csv.gz"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

                    storageService.setFileStorageState(destId, FileStorageState.UNSET);
                }
                catch (Exception ex) {
                    logger.error("Failed to export blacklist", ex);
                    storageService.setFileStorageState(destId, FileStorageState.DELETE);
                    yield new Error("Failed to export blacklist");
                }
                finally {
                    Files.deleteIfExists(tmpFile);
                }

                yield new End();
            }
            default -> new Error();
        };
    }

    private boolean exportLinks(ATagCsvWriter exporter, SerializableCrawlDataStream stream) throws IOException, URISyntaxException {
        final TLongHashSet hashes = new TLongHashSet();

        while (stream.hasNext()) {
            if (!(stream.next() instanceof CrawledDocument doc))
                continue;
            if (null == doc.documentBody)
                continue;

            var baseUrl = new EdgeUrl(doc.url);
            var parsed = Jsoup.parse(doc.documentBody);

            for (var atag : parsed.getElementsByTag("a")) {
                String linkText = atag.text();
                if (linkText.isBlank())
                    continue;

                var linkOpt = linkParser.parseLinkPermissive(baseUrl, atag);
                linkOpt
                        .filter(url -> !Objects.equals(url.domain, baseUrl.domain))
                        .filter(url -> hashes.add(hash.hashNearlyASCII(linkText) ^ hash.hashNearlyASCII(url.toString())))
                        .ifPresent(url -> exporter.accept(url, baseUrl.domain, linkText));
            }
        }

        return true;
    }

    private static class ATagCsvWriter {
        private final BufferedWriter writer;

        private ATagCsvWriter(BufferedWriter writer) {
            this.writer = writer;
        }

        @SneakyThrows
        public void accept(EdgeUrl url, EdgeDomain domain, String linkText) {
            writer.write(String.format("\"%s\",\"%s\",\"%s\"\n",
                    csvify(url),
                    csvify(domain),
                    csvify(linkText)));
        }

        private static String csvify(Object field) {
            return field.toString()
                    .replace("\"", "\"\"")
                    .replace("\n", " ");
        }

    }

    @Override
    public String describe() {
        return "Export anchor tags from crawl data";
    }

    @Inject
    public ExportAtagsActor(Gson gson,
                            FileStorageService storageService)
    {
        super(gson);
        this.storageService = storageService;
    }

}
