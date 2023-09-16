package nu.marginalia.crawling.io;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.model.gson.GsonFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class CrawledDomainWriter implements AutoCloseable {
    private final Path outputDir;
    private final Gson gson = GsonFactory.get();
    private final Writer writer;
    private final Path tmpFile;
    private final Path actualFile;

    public CrawledDomainWriter(Path outputDir, String domain, String id) throws IOException {
        this.outputDir = outputDir;

        if (!Files.isDirectory(outputDir)) {
            throw new IllegalArgumentException("Output dir " + outputDir + " does not exist");
        }


        // Do the actual writing to a temporary file first, then move it to the actual file when close() is invoked
        // this lets us read the old file and compare its contents while writing the new file.  It also guards against
        // half-written files if the process is killed.

        tmpFile = getOutputFile(id, domain + "_tmp");
        actualFile = getOutputFile(id, domain);
        writer =  new OutputStreamWriter(new ZstdOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)),
                RecyclingBufferPool.INSTANCE));
    }

    public Path getOutputFile() {
        return actualFile;
    }

    @SneakyThrows
    public void accept(SerializableCrawlData data) {
        writer.write(data.getSerialIdentifier());
        writer.write('\n');
        gson.toJson(data, writer);
        writer.write('\n');
    }

    private Path getOutputFile(String id, String name) throws IOException {
        return CrawlerOutputFile.createOutputPath(outputDir, id, name);
    }

    @Override
    public void close() throws IOException {
        Files.move(tmpFile, actualFile, StandardCopyOption.REPLACE_EXISTING);
        writer.close();
    }
}
