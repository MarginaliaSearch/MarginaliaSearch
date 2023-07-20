package nu.marginalia.crawling.io;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(CrawledDomainWriter.class);
    private final Writer writer;
    private final Path tmpFile;
    private final Path outputFile;

    public CrawledDomainWriter(Path outputDir, String name, String id) throws IOException {
        this.outputDir = outputDir;

        if (!Files.isDirectory(outputDir)) {
            throw new IllegalArgumentException("Output dir " + outputDir + " does not exist");
        }

        tmpFile = getOutputFile(id, name + "_tmp");
        outputFile = getOutputFile(id, name);
        writer =  new OutputStreamWriter(new ZstdOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))));
    }

    public Path getOutputFile() {
        return outputFile;
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
        Files.move(tmpFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
        writer.close();
    }
}
