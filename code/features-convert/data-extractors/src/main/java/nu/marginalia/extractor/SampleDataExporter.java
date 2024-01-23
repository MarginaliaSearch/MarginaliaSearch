package nu.marginalia.extractor;

import com.google.inject.Inject;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;

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

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

public class SampleDataExporter {
    private final FileStorageService storageService;

    @Inject
    public SampleDataExporter(FileStorageService storageService) {
        this.storageService = storageService;
    }
    public void export(FileStorageId crawlId, FileStorageId destId, int size, String name) throws SQLException, IOException {
        FileStorage destStorage = storageService.getStorage(destId);
        Path inputDir = storageService.getStorage(crawlId).asPath();

        Path crawlerLogFile = inputDir.resolve("crawler.log");

        List<WorkLogEntry> entriesAll = new ArrayList<>(100_000);

        for (var item : WorkLog.iterable(crawlerLogFile)) {
            if (item.cnt() < 2) continue;
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
                bw.write(STR."\{item.id()} \{item.ts()} \{item.relPath()} \{item.cnt()}\n");
            }
        }

        Path newManifestJsonFile = Files.createTempFile(destStorage.asPath(), "manifest", ".json",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));
        Files.writeString(newManifestJsonFile, STR."""
            { "description": "\{name.replace("[\"\\]", "_")}",
              "type": "CRAWL_DATA" }
        """);

        var tmpTarFile = Files.createTempFile(destStorage.asPath(), "data", ".tar",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

        try (var stream = new TarArchiveOutputStream(Files.newOutputStream(tmpTarFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (var item : entriesAll) {
                Path crawlDataPath = inputDir.resolve(item.relPath());
                if (!Files.exists(crawlDataPath)) continue;

                addFileToTar(stream, crawlDataPath, item.relPath());
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
