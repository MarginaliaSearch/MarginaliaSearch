package nu.marginalia.index.journal.reader;

import com.github.luben.zstd.ZstdInputStream;
import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryTermData;
import nu.marginalia.index.journal.model.IndexJournalFileHeader;
import nu.marginalia.index.journal.reader.pointer.IndexJournalPointer;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

public class IndexJournalReaderSingleFile implements IndexJournalReader {

    private final Path journalFile;
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
        if (++docIdx < fileHeader.fileSizeRecords()) {
            entry = IndexJournalReadEntry.read(dataInputStream);
            return true;
        }

        dataInputStream.close();

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
    public int documentFeatures() { return entry.documentFeatures(); }

    @Override
    public int documentSize() { return entry.documentSize(); }

    /** Return an iterator over the terms in the current document.
     *  This iterator is not valid after calling nextDocument().
     */
    @NotNull
    @Override
    public Iterator<IndexJournalEntryTermData> iterator() {
        return entry.iterator();
    }

    @Override
    public void close() throws IOException {
        dataInputStream.close();
    }
}