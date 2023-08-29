package nu.marginalia.index.journal.reader;

import com.github.luben.zstd.ZstdInputStream;
import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalFileHeader;
import nu.marginalia.index.journal.model.IndexJournalStatistics;
import org.jetbrains.annotations.NotNull;
import org.roaringbitmap.longlong.Roaring64Bitmap;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.IntConsumer;
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
    final Predicate<IndexJournalEntryData.Record> recordPredicate;

    public IndexJournalReaderSingleCompressedFile(Path file) throws IOException {
        this.journalFile = file;

        fileHeader = readHeader(file);

        this.recordPredicate = null;
        this.entryPredicate = null;
    }

    public IndexJournalReaderSingleCompressedFile(Path file, Predicate<IndexJournalReadEntry> entryPredicate, Predicate<IndexJournalEntryData.Record> recordPredicate) throws IOException {
        this.journalFile = file;

        fileHeader = readHeader(file);

        this.recordPredicate = recordPredicate;
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

    public boolean filter(IndexJournalReadEntry entry) {
        return entryPredicate == null || entryPredicate.test(entry);
    }

    public boolean filter(IndexJournalReadEntry entry, IndexJournalEntryData.Record record) {
        return (entryPredicate == null || entryPredicate.test(entry))
            && (recordPredicate == null || recordPredicate.test(record));
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
        private int i = 0;

        @Override
        @SneakyThrows
        public boolean hasNext() {
            return i < fileHeader.fileSize();
        }

        @SneakyThrows
        @Override
        public IndexJournalReadEntry next() {
            i++;
            return IndexJournalReadEntry.read(dataInputStream);
        }

    }

}
