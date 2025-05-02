package nu.marginalia.extractor;

import com.google.inject.Inject;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import nu.marginalia.slop.SlopCrawlDataRecord;
import nu.marginalia.slop.SlopTablePacker;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SampleDataExporter {
    private final FileStorageService storageService;

    @Inject
    public SampleDataExporter(FileStorageService storageService) {
        this.storageService = storageService;
    }
    public void export(FileStorageId crawlId, FileStorageId destId, int size, String ctFilter, String name) throws SQLException, IOException {
        FileStorage destStorage = storageService.getStorage(destId);
        Path inputDir = storageService.getStorage(crawlId).asPath();

        Path crawlerLogFile = inputDir.resolve("crawler.log");

        List<WorkLogEntry> entriesAll = new ArrayList<>(100_000);

        for (var item : WorkLog.iterable(crawlerLogFile)) {
            if (item.cnt() < 2) // this one's too small
                continue;
            if (item.cnt() > 5000) // this one's too big
                continue;
            if (item.relPath().length() > 90) // this one's too long
                continue; // TAR file name limit is 100, but we add some extra for good measure

            // this one's just right
            entriesAll.add(item);
        }

        if (entriesAll.size() > size) {
            Collections.shuffle(entriesAll);
            entriesAll = entriesAll.subList(0, size);
        }

        Path newCrawlerLogFile = Files.createTempFile(destStorage.asPath(), "crawler", ".log",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

        try (var bw = Files.newBufferedWriter(newCrawlerLogFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (var item : entriesAll) {
                bw.write(item.id() + " " + item.ts() + " " + item.relPath() + " " + item.cnt() + "\n");
            }
        }

        Path newManifestJsonFile = Files.createTempFile(destStorage.asPath(), "manifest", ".json",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));
        Files.writeString(newManifestJsonFile, " { \"description\": \"" + name.replace("[\"\\]", "_") + "\",\n      \"type\": \"CRAWL_DATA\" }\n");

        var tmpTarFile = Files.createTempFile(destStorage.asPath(), "data", ".tar",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

        try (var stream = new TarArchiveOutputStream(Files.newOutputStream(tmpTarFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (var item : entriesAll) {
                Path crawlDataPath = inputDir.resolve(item.relPath());
                if (!Files.exists(crawlDataPath)) continue;

                if (StringUtils.isBlank(ctFilter)) {
                    addFileToTar(stream, crawlDataPath, item.relPath());
                }
                else /* filter != null */ {
                    boolean didFilterData = false;
                    try {
                        crawlDataPath = filterEntries(crawlDataPath, ctFilter);
                        didFilterData = true;
                        addFileToTar(stream, crawlDataPath, item.relPath());
                    }
                    finally {
                        if (didFilterData) {
                            Files.deleteIfExists(crawlDataPath);
                        }
                    }
                }
            }

            addFileToTar(stream, newCrawlerLogFile, "crawler.log");
            addFileToTar(stream, newManifestJsonFile, "marginalia-manifest.json");
        }
        finally {
            Files.deleteIfExists(newCrawlerLogFile);
            Files.deleteIfExists(newManifestJsonFile);
        }

        Files.move(tmpTarFile, destStorage.asPath().resolve("crawl-data.tar"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Filters the entries in the crawl data file based on the content type.
     * @param crawlDataPath The path to the crawl data file.
     * @param contentTypeFilter The content type to filter by.
     * @return The path to the filtered crawl data file, or null if an error occurred.
     */
    private Path filterEntries(Path crawlDataPath, String contentTypeFilter) throws IOException {
        Path tempDir = crawlDataPath.resolveSibling(crawlDataPath.getFileName() + ".filtered");
        Path tempFile = crawlDataPath.resolveSibling(crawlDataPath.getFileName() + ".filtered.slop.zip");

        Files.createDirectory(tempDir);

        try (var writer = new SlopCrawlDataRecord.Writer(tempDir);
             var reader = new SlopCrawlDataRecord.FilteringReader(crawlDataPath) {
                 @Override
                 public boolean filter(String url, int status, String contentType) {
                     if (contentTypeFilter.equals(contentType))
                         return true;
                     else if (contentType.startsWith("x-marginalia/"))
                         // This is a metadata entry, typically domain or redirect information
                         // let's keep those to not confuse the consumer of the data, which might
                         // expect at least the domain summary
                         return true;
                     return false;
                 }
             }
        ) {
            while (reader.hasRemaining()) {
                writer.write(reader.get());
            }

            SlopTablePacker.packToSlopZip(tempDir, tempFile);
        }
        finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }


        return tempFile;
    }

    private void addFileToTar(TarArchiveOutputStream outputStream, Path file, String fileName) throws IOException {
        var entry = outputStream.createArchiveEntry(file.toFile(), fileName);
        entry.setSize(Files.size(file));
        outputStream.putArchiveEntry(entry);

        try (var fis = Files.newInputStream(file)) {
            IOUtils.copy(fis, outputStream);
        } finally {
            outputStream.closeArchiveEntry();
        }
    }
}
