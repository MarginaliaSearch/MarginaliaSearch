package nu.marginalia.converting;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class InstructionWriterFactory {

    private final ConversionLog log;
    private final Path outputDir;
    private final Gson gson;
    private static final Logger logger = LoggerFactory.getLogger(InstructionWriterFactory.class);

    public InstructionWriterFactory(ConversionLog log, Path outputDir, Gson gson) {
        this.log = log;
        this.outputDir = outputDir;
        this.gson = gson;

        if (!Files.isDirectory(outputDir)) {
            throw new IllegalArgumentException("Output dir " + outputDir + " does not exist");
        }
    }

    public InstructionWriter createInstructionsForDomainWriter(String id) throws IOException {
        Path outputFile = getOutputFile(id);
        return new InstructionWriter(outputFile);
    }

    public class InstructionWriter implements AutoCloseable {
        private final ObjectOutputStream outputStream;
        private final String where;
        private final SummarizingInterpreter summary = new SummarizingInterpreter();

        private int size = 0;


        InstructionWriter(Path filename) throws IOException {
            where = filename.getFileName().toString();
            Files.deleteIfExists(filename);
            outputStream = new ObjectOutputStream(new ZstdOutputStream(new FileOutputStream(filename.toFile())));
        }

        public void accept(Instruction instruction) {
            if (instruction.isNoOp()) return;

            instruction.apply(summary);
            instruction.apply(log);

            size++;

            try {
                outputStream.writeObject(instruction);

                // Reset the stream to avoid keeping references to the objects
                // (as this will cause the memory usage to grow indefinitely when
                // writing huge amounts of data)
                outputStream.reset();
            }
            catch (IOException ex) {
                logger.warn("IO exception writing instruction", ex);
            }
        }

        @Override
        public void close() throws IOException {
            logger.info("Wrote {} - {} - {}", where, size, summary);
            outputStream.close();
        }

        public String getFileName() {
            return where;
        }

        public int getSize() {
            return size;
        }
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

        private String domainName;
        private int ok = 0;
        private int error = 0;

        int keywords = 0;
        int documents = 0;

        public String toString() {
            // This shouldn't happen (TM)
            assert keywords == documents : "keywords != documents";

            return String.format("%s - %d %d", domainName, ok, error);
        }

        @Override
        public void loadProcessedDomain(EdgeDomain domain, DomainIndexingState state, String ip) {
            this.domainName = domain.toString();
        }

        @Override
        public void loadProcessedDocument(LoadProcessedDocument loadProcessedDocument) {
            documents++;
        }

        @Override
        public void loadKeywords(EdgeUrl url, int ordinal, int features, DocumentMetadata metadata, DocumentKeywords words) {
            keywords++;
        }

        @Override
        public void loadDomainMetadata(EdgeDomain domain, int knownUrls, int goodUrls, int visitedUrls) {
            ok += goodUrls;
            error += visitedUrls - goodUrls;
        }
    }
}
