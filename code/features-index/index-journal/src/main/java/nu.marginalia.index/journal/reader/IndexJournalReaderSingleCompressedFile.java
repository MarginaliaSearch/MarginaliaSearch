package nu.marginalia.index.journal.reader;

import com.github.luben.zstd.ZstdInputStream;
import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalFileHeader;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

public class IndexJournalReaderSingleCompressedFile implements IndexJournalReader {

    private Path journalFile;
    public final IndexJournalFileHeader fileHeader;

    @Override
    public String toString() {
        return "IndexJournalReaderSingleCompressedFile{" + journalFile + " }";
    }

    private DataInputStream dataInputStream = null;

    final Predicate<IndexJournalReadEntry> entryPredicate;
    final Predicate<Long> metadataPredicate;

    public IndexJournalReaderSingleCompressedFile(Path file) throws IOException {
        this.journalFile = file;

        fileHeader = readHeader(file);

        this.metadataPredicate = null;
        this.entryPredicate = null;
    }

    public IndexJournalReaderSingleCompressedFile(Path file, Predicate<IndexJournalReadEntry> entryPredicate, Predicate<Long> metadataPredicate) throws IOException {
        this.journalFile = file;

        fileHeader = readHeader(file);

        this.metadataPredicate = metadataPredicate;
        this.entryPredicate = entryPredicate;
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

    @Override
    public boolean filter(IndexJournalReadEntry entry) {
        return entryPredicate == null || entryPredicate.test(entry);
    }

    @Override
    public boolean filter(IndexJournalReadEntry entry, IndexJournalEntryData.Record record) {
        return (entryPredicate == null || entryPredicate.test(entry))
            && (metadataPredicate == null || metadataPredicate.test(record.metadata()));
    }

    @Override
    public boolean filter(IndexJournalReadEntry entry, long metadata) {
        return (entryPredicate == null || entryPredicate.test(entry))
                && (metadataPredicate == null || metadataPredicate.test(metadata));
    }

    public void close() throws IOException {
        dataInputStream.close();
    }


    @Override
    public void forEachWordId(LongConsumer consumer) {
        for (var entry : this) {
            var data = entry.readEntry();
            for (var post : data) {
                if (filter(entry, post)) {
                    consumer.accept(post.wordId());
                }
            }
        }
    }

    @Override
    public void forEachDocIdRecord(LongObjectConsumer<IndexJournalEntryData.Record> consumer) {
        for (var entry : this) {
            var data = entry.readEntry();

            for (var post : data) {
                if (filter(entry, post)) {
                    consumer.accept(entry.docId(), post);
                }
            }
        }
    }
    @Override
    public void forEachDocId(LongConsumer consumer) {
        for (var entry : this) {
            if (filter(entry)) {
                consumer.accept(entry.docId());
            }
        }
    }

    @SneakyThrows
    @NotNull
    @Override
    public Iterator<IndexJournalReadEntry> iterator() {
        if (dataInputStream != null) {
            dataInputStream.close();
        }
        dataInputStream = createInputStream(journalFile);

        return new JournalEntryIterator();
    }

    private class JournalEntryIterator implements Iterator<IndexJournalReadEntry> {
        private int i = -1;
        private IndexJournalReadEntry next;

        @Override
        @SneakyThrows
        public boolean hasNext() {
            if (next != null)
                return true;

            while (++i < fileHeader.fileSize()) {
                var entry = IndexJournalReadEntry.read(dataInputStream);
                if (filter(entry)) {
                    next = entry;
                    return true;
                }
            }

            return false;
        }

        @SneakyThrows
        @Override
        public IndexJournalReadEntry next() {
            if (hasNext()) {
                var ret = next;
                next = null;
                return ret;
            }
            throw new IllegalStateException();
        }

    }

}
