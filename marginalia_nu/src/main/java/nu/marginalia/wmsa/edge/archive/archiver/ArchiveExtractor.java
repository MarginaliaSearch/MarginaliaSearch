package nu.marginalia.wmsa.edge.archive.archiver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.archive.request.EdgeArchiveSubmissionReq;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class ArchiveExtractor {
    private final Path archivePath;
    private final String arhivePattern = "archive-%04d.tar.gz";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = new GsonBuilder().create();

    public ArchiveExtractor(Path archivePath) {
        this.archivePath = archivePath;

    }

    public void forEach(Consumer<EdgeRawPageContents> contents) {
        for (int i = 0; ; ++i) {
            var fn = getArchiveFile(i);
            logger.info("{}", fn);
            if (!Files.exists(fn)) {
                break;
            }
            try (var stream = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(fn.toFile()))))) {
                TarArchiveEntry entry;
                while ((entry = stream.getNextTarEntry()) != null) {
                    if (entry.isFile()) {
                        try {
                            var obj = gson.fromJson(new InputStreamReader(stream), EdgeArchiveSubmissionReq.class);
                            if (obj != null) {
                                contents.accept(obj.getData());
                            }
                        }
                        catch (Exception ex) {
                            logger.error("Could not unpack {} - {} {}", entry.getName(), ex.getClass().getSimpleName(), ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Path getArchiveFile(int number) {
        final String fileName = String.format(arhivePattern, number);
        return archivePath.resolve(fileName);
    }
}

