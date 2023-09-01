package nu.marginalia.index.journal.reader;

import com.github.luben.zstd.ZstdInputStream;
import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalFileHeader;
import nu.marginalia.index.journal.reader.pointer.IndexJournalPointer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IndexJournalReaderSingleFile implements IndexJournalReader {

    private Path journalFile;
    public final IndexJournalFileHeader fileHeader;

    @Override
    public String toString() {
        return "IndexJournalReaderSingleCompressedFile{" + journalFile + " }";
    }

    public IndexJournalReaderSingleFile(Path file) throws IOException {
        this.journalFile = file;

        fileHeader = readHeader(file);
    }

    private static IndexJournalFileHeader readHeader(Path file) throws IOException {
        try (var raf = new RandomAccessFile(file.toFile(), "r")) {
            long unused = raf.readLong();
            long wordCount = raf.readLong();

            return new IndexJournalFileHeader(unused, wordCount);
        }
    }

    private static DataInputStream createInputStream(Path file) throws IOException {
        var fileInputStream = Files.newInputStream(file, StandardOpenOption.READ);

        // skip the header
        fileInputStream.skipNBytes(16);

        return new DataInputStream(new ZstdInputStream(new BufferedInputStream(fileInputStream)));
    }

    @SneakyThrows
    @Override
    public IndexJournalPointer newPointer() {
        return new SingleFileJournalPointer(fileHeader, createInputStream(journalFile));
    }

}

class SingleFileJournalPointer implements IndexJournalPointer {

    private final IndexJournalFileHeader fileHeader;
    private final DataInputStream dataInputStream;
    private IndexJournalReadEntry entry;
    private IndexJournalEntryData entryData;
    private int recordIdx = -2;
    private int docIdx = -1;

    public SingleFileJournalPointer(
            IndexJournalFileHeader fileHeader,
            DataInputStream dataInputStream)
    {
        this.fileHeader = fileHeader;
        this.dataInputStream = dataInputStream;
    }

    @SneakyThrows
    @Override
    public boolean nextDocument() {
        recordIdx = -2;
        entryData = null;

        if (++docIdx < fileHeader.fileSize()) {
            entry = IndexJournalReadEntry.read(dataInputStream);
            return true;
        }

        dataInputStream.close();

        return false;
    }

    @Override
    public boolean nextRecord() {
        if (entryData == null) {
            entryData = entry.readEntry();
        }

        recordIdx += 2;
        if (recordIdx < entryData.size()) {
            return true;
        }
        return false;
    }

    @Override
    public long documentId() {
        return entry.docId();
    }

    @Override
    public long documentMeta() {
        return entry.docMeta();
    }

    @Override
    public long wordId() {
        return entryData.get(recordIdx);
    }

    @Override
    public long wordMeta() {
        return entryData.get(recordIdx + 1);
    }

    @Override
    public int documentFeatures() {
        if (entryData == null) {
            entryData = entry.readEntry();
        }

        return entry.header.documentFeatures();
    }
}