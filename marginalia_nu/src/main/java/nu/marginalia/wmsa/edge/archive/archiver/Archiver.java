package nu.marginalia.wmsa.edge.archive.archiver;

import com.google.inject.name.Named;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

@Singleton
public class Archiver implements  AutoCloseable {
    private final Path archivePath;
    private final int filesPerArchive;
    private final String arhivePattern = "archive-%04d.tar.gz";

    private final LinkedBlockingDeque<ArchivedFile> writeQueue = new LinkedBlockingDeque<>(10);
    private final Thread writeThread;

    private volatile int archiveNumber;
    private volatile boolean running;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public Archiver(@Named("archive-path") Path archivePath, @Named("archive-size") Integer filesPerArchive) {
        this.archivePath = archivePath;
        this.filesPerArchive = filesPerArchive;

        if (!Files.exists(archivePath)) {
            throw new IllegalArgumentException("Archive path does not exist");
        }
        for (int i = 0;; ++i) {
            if (!Files.exists(getArchiveFile(i))) {
                archiveNumber = i;
                break;
            }
        }

        running = true;
        writeThread = new Thread(this::writeThreadMain, "ArchiveWriteThread");
        writeThread.start();
    }

    private Path getArchiveFile(int number) {
        final String fileName = String.format(arhivePattern, number);
        return archivePath.resolve(fileName);
    }

    public void writeData(ArchivedFile file) throws InterruptedException {
        if (!running) throw new IllegalStateException("Archiver is closing or closed");
        writeQueue.put(file);
    }

    private void writeThreadMain() {
        try {
            while (running || !writeQueue.isEmpty()) {
                writeToFile(archiveNumber);
                archiveNumber++;
            }
            running = false;
        }
        catch (Exception ex) {
            logger.error("Uncaught exception in writer thread!!");
        }
    }

    private void writeToFile(int archiveNumber) {
        var archiveFile = getArchiveFile(archiveNumber);

        logger.info("Switching to file {}", archiveFile);

        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(archiveFile.toFile())))) {
            for (int i = 0; i < filesPerArchive; i++) {

                ArchivedFile writeJob = null;
                while (writeJob == null) {
                    writeJob = writeQueue.poll(1, TimeUnit.SECONDS);
                    if (!running) return;
                }

                var entry = new TarArchiveEntry(String.format("%06d-%s", i, writeJob.filename()));
                entry.setSize(writeJob.data().length);
                taos.putArchiveEntry(entry);
                logger.debug("Writing {} to {}", writeJob.filename(), archiveFile);
                try (var bais = new ByteArrayInputStream(writeJob.data())) {
                    IOUtils.copy(bais, taos);
                }
                taos.closeArchiveEntry();
            }
            taos.finish();
            logger.debug("Finishing {}", archiveFile);
        } catch (Exception e) {
            logger.error("Error", e);
        }

    }

    @Override
    public void close() throws Exception {
        running = false;
        writeThread.join();
    }
}
