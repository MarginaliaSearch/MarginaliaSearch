package nu.marginalia.wmsa.edge.crawling;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.edge.crawling.model.SerializableCrawlData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class CrawledDomainWriter implements AutoCloseable {
    private final Path outputDir;
    private final Gson gson = GsonFactory.get();
    private static final Logger logger = LoggerFactory.getLogger(CrawledDomainWriter.class);
    private final Writer writer;
    private final Path outputFile;

    public CrawledDomainWriter(Path outputDir, String name, String id) throws IOException {
        this.outputDir = outputDir;

        if (!Files.isDirectory(outputDir)) {
            throw new IllegalArgumentException("Output dir " + outputDir + " does not exist");
        }

        outputFile = getOutputFile(id, name);
        writer =  new OutputStreamWriter(new ZstdOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile))));
    }

    public Path getOutputFile() {
        return outputFile;
    }

    public void accept(SerializableCrawlData data) throws IOException {
        writer.write(data.getSerialIdentifier());
        writer.write('\n');
        gson.toJson(data, writer);
        writer.write('\n');
    }

    private Path getOutputFile(String id, String name) throws IOException {
        String first = id.substring(0, 2);
        String second = id.substring(2, 4);

        Path destDir = outputDir.resolve(first).resolve(second);
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }
        return destDir.resolve(id + "-" + filesystemSafeName(name) + ".zstd");
    }

    private String filesystemSafeName(String name) {
        StringBuilder nameSaneBuilder = new StringBuilder();

        name.chars()
                .map(Character::toLowerCase)
                .map(c -> (c & ~0x7F) == 0 ? c : 'X')
                .map(c -> (Character.isDigit(c) || Character.isAlphabetic(c) || c == '.') ? c : 'X')
                .limit(128)
                .forEach(c -> nameSaneBuilder.append((char) c));

        return nameSaneBuilder.toString();

    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
