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
import org.apache.commons.lang3.StringUtils;
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
        ATagLinkFilter linkFilter = new ATagLinkFilter();

        while (stream.hasNext()) {
            if (!(stream.next() instanceof CrawledDocument doc))
                continue;
            if (null == doc.documentBody)
                continue;

            var baseUrl = new EdgeUrl(doc.url);
            var parsed = Jsoup.parse(doc.documentBody);

            for (var atag : parsed.getElementsByTag("a")) {
                String linkText = atag.text();

                if (!linkFilter.isLinkTextEligible(linkText)) {
                    continue;
                }

                var linkOpt = linkParser.parseLinkPermissive(baseUrl, atag);
                linkOpt
                        .filter(url -> linkFilter.isEligible(url, baseUrl, linkText))
                        .ifPresent(url -> exporter.accept(url, baseUrl.domain, linkText));
            }
        }

        return true;
    }

    private static class ATagLinkFilter {
        private final TLongHashSet hashes = new TLongHashSet();

        private boolean isLinkTextEligible(String linkText) {
            // Filter out the most obviously uninteresting anchor texts

            if (linkText.isBlank())
                return false;
            if (linkText.startsWith("this"))
                return false;
            if (linkText.equalsIgnoreCase("here"))
                return false;
            if (linkText.equalsIgnoreCase("click here"))
                return false;

            if (!StringUtils.isAsciiPrintable(linkText))  // This also filters out newlines, a good thing!
                return false;

            return true;
        }
        private boolean isEligible(EdgeUrl url, EdgeUrl baseUrl, String linkText) {
            if (!"http".equals(url.proto) && !"https".equals(url.proto))
                return false;

            // This is an artifact of the link parser typically
            if ("example.com".equals(url.domain.domain))
                return false;

            if (linkText.contains(url.domain.toString()))
                return false;
            if (Objects.equals(url.domain, baseUrl.domain))
                return false;

            String urlString = url.toString();
            if (!StringUtils.isAsciiPrintable(urlString)) { // This also filters out newlines, a good thing!
                return false;
            }

            // Deduplicate by hash;  we've already checked that the strings are ASCII printable so we don't
            // need to be concerned about using the fast ASCII hash
            if (hashes.add(hash.hashLowerBytes(linkText) ^ hash.hashLowerBytes(urlString))) {
                return false;
            }

            return true;
        }
    }


    private static class ATagCsvWriter {
        private final BufferedWriter writer;

        private ATagCsvWriter(BufferedWriter writer) {
            this.writer = writer;
        }

        @SneakyThrows
        public void accept(EdgeUrl url, EdgeDomain sourceDomain, String linkText) {
            final String urlString = urlWithNoSchema(url);

            writer.write(String.format("\"%s\",\"%s\",\"%s\"\n",
                    csvify(urlString),
                    csvify(linkText),
                    csvify(sourceDomain)));
        }

        private static String urlWithNoSchema(EdgeUrl url) {
            StringBuilder sb = new StringBuilder();

            sb.append(url.domain).append(url.path);

            if (url.param != null)
                sb.append('?').append(url.param);

            return sb.toString();
        }

        private static String csvify(Object field) {
            return field.toString().replace("\"", "\"\"");
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
