package nu.marginalia.wmsa.edge.converting;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CrawledInstructionWriter {
    private final Path outputDir;
    private Gson gson;
    private static final Logger logger = LoggerFactory.getLogger(CrawledInstructionWriter.class);

    public CrawledInstructionWriter(Path outputDir, Gson gson) {
        this.outputDir = outputDir;
        this.gson = gson;

        if (!Files.isDirectory(outputDir)) {
            throw new IllegalArgumentException("Output dir " + outputDir + " does not exist");
        }
    }

    public String accept(String id, List<Instruction> instructionList) throws IOException {
        Path outputFile = getOutputFile(id);

        if (Files.exists(outputFile)) {
            Files.delete(outputFile);
        }

        try (var outputStream = new OutputStreamWriter(new ZstdOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile.toFile()))))) {
            logger.info("Writing {} - {}", id, instructionList.size());

            for (var instr : instructionList) {
                outputStream.append(instr.tag().name());
                outputStream.append(' ');
                gson.toJson(instr, outputStream);
                outputStream.append('\n');
            }
        }

        return outputFile.getFileName().toString();
    }

    private Path getOutputFile(String id) throws IOException {
        String first = id.substring(0, 2);
        String second = id.substring(2, 4);

        Path destDir = outputDir.resolve(first).resolve(second);
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }
        return destDir.resolve(id + ".pzstd");
    }
}
