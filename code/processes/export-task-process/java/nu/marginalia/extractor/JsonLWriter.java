package nu.marginalia.extractor;

import com.google.gson.Gson;
import nu.marginalia.model.gson.GsonFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;

/** Utility for exporting oject streams as a series of JSONL files that are gzip compressed */
public class JsonLWriter<T> implements AutoCloseable {
    private final Gson gson = GsonFactory.get();

    private final Path basePath;
    private final String prefix;
    private final int recordsPerFile;

    private int recordIndex = 0;
    private int fileIndex = 0;

    private Path currentFile = null;
    private Writer currentWriter = null;

    public JsonLWriter(Path basePath, String prefix, int recordsPerFile) throws IOException {
        this.basePath = basePath;
        this.prefix = prefix;
        this.recordsPerFile = recordsPerFile;

        switchWriter();
    }

    public void write(T object) throws IOException {
        if (++recordIndex > recordsPerFile)
            switchWriter();

        currentWriter.write(gson.toJson(object));
        currentWriter.write('\n');
    }

    private void switchWriter() throws IOException {
        if (currentWriter != null) {
            currentWriter.close();
        }

        currentFile = basePath.resolve("%s-%03d.jsonl.gz".formatted(prefix, fileIndex++));
        currentWriter = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(currentFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)));
        recordIndex = 0;
    }

    public void close() throws IOException {
        currentWriter.close();
    }
}
