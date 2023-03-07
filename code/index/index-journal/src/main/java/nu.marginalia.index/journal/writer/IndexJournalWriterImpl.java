package nu.marginalia.index.journal.writer;

import com.github.luben.zstd.ZstdOutputStream;
import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.lexicon.KeywordLexicon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IndexJournalWriterImpl implements IndexJournalWriter{
    private final KeywordLexicon lexicon;
    private final Path outputFile;
    private final DataOutputStream outputStream;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int numEntries = 0;

    public IndexJournalWriterImpl(KeywordLexicon lexicon, Path outputFile) throws IOException {
        this.lexicon = lexicon;
        this.outputFile = outputFile;

        var fileStream = Files.newOutputStream(outputFile, StandardOpenOption.CREATE);

        writeHeaderPlaceholder(fileStream);

        outputStream = new DataOutputStream(new ZstdOutputStream(fileStream));
    }

    private static void writeHeaderPlaceholder(OutputStream fileStream) throws IOException {
        fileStream.write(new byte[IndexJournalReader.FILE_HEADER_SIZE_BYTES]);
    }

    @Override
    @SneakyThrows
    public void put(IndexJournalEntryHeader header, IndexJournalEntryData entry) {
        outputStream.writeInt(entry.size());
        outputStream.writeInt(0);
        outputStream.writeLong(header.combinedId());
        outputStream.writeLong(header.documentMeta());
        entry.write(outputStream);

        numEntries++;
    }

    @Override
    public void forceWrite() throws IOException {
        outputStream.flush();

        try (var raf = new RandomAccessFile(outputFile.toFile(), "rws")) {
            raf.writeLong(numEntries);
            raf.writeLong(lexicon.size());
        }
    }

    @Override
    public void flushWords() {
        lexicon.commitToDisk();
    }

    public void close() throws IOException {
        forceWrite();

        outputStream.close();
    }
}
