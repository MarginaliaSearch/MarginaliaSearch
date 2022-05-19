package nu.marginalia.wmsa.edge.crawling;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class CrawledDomainWriter {
    private final Path outputDir;
    private final Gson gson = new GsonBuilder().create();
    private static final Logger logger = LoggerFactory.getLogger(CrawledDomainWriter.class);

    public CrawledDomainWriter(Path outputDir) {
        this.outputDir = outputDir;

        if (!Files.isDirectory(outputDir)) {
            throw new IllegalArgumentException("Output dir " + outputDir + " does not exist");
        }
    }

    public String accept(CrawledDomain domainData) throws IOException {
        Path outputFile = getOutputFile(domainData.id, domainData.domain);

        try (var outputStream = new OutputStreamWriter(new ZstdOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile.toFile()))))) {
            logger.info("Writing {} - {}", domainData.id, domainData.domain);

            gson.toJson(domainData, outputStream);
        }

        return outputFile.getFileName().toString();
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
}
