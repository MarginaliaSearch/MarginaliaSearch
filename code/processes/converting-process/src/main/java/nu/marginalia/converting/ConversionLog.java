package nu.marginalia.converting;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdOutputStream;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class ConversionLog implements AutoCloseable, Interpreter {
    private final PrintWriter writer;

    public ConversionLog(Path rootDir) throws IOException {
        String fileName = String.format("conversion-log-%s.zstd", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        Path logFile = rootDir.resolve(fileName);

        writer = new PrintWriter(new ZstdOutputStream(new BufferedOutputStream(Files.newOutputStream(logFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)), RecyclingBufferPool.INSTANCE));
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @Override
    public synchronized void loadProcessedDocumentWithError(LoadProcessedDocumentWithError loadProcessedDocumentWithError) {
        writer.printf("%s\t%s\n", loadProcessedDocumentWithError.url(), loadProcessedDocumentWithError.reason());
    }

}
