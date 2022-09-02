package nu.marginalia.wmsa.edge.converting;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocument;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocumentWithError;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
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
    private final Gson gson;
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

            SummarizingInterpreter summary = new SummarizingInterpreter(instructionList);
            logger.info("Writing {} - {} - {}", id, instructionList.size(), summary);

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

    private static class SummarizingInterpreter implements Interpreter {

        private SummarizingInterpreter(List<Instruction> instructions) {
            for (var i : instructions) {
                i.apply(this);
            }
        }

        private String domainName;
        private int ok = 0;
        private int error = 0;

        public String toString() {
            return String.format("%s - %d %d", domainName, ok, error);
        }

        @Override
        public void loadUrl(EdgeUrl[] url) {}

        @Override
        public void loadDomain(EdgeDomain[] domain) {}

        @Override
        public void loadRssFeed(EdgeUrl[] rssFeed) {}

        @Override
        public void loadDomainLink(DomainLink[] links) {}

        @Override
        public void loadProcessedDomain(EdgeDomain domain, EdgeDomainIndexingState state, String ip) {
            this.domainName = domain.toString();
        }

        @Override
        public void loadProcessedDocument(LoadProcessedDocument loadProcessedDocument) {
            ok++;
        }

        @Override
        public void loadProcessedDocumentWithError(LoadProcessedDocumentWithError loadProcessedDocumentWithError) {
            error++;
        }

        @Override
        public void loadKeywords(EdgeUrl url, DocumentKeywords[] words) {}

        @Override
        public void loadDomainRedirect(DomainLink link) {}
    }
}
