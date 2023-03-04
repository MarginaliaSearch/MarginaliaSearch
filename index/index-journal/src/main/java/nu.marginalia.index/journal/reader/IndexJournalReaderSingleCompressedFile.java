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
import java.util.Iterator;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public class IndexJournalReaderSingleCompressedFile implements IndexJournalReader {

    private static Path journalFile;
    public final IndexJournalFileHeader fileHeader;

    private DataInputStream dataInputStream = null;

    final Predicate<IndexJournalReadEntry> entryPredicate;
    final Predicate<IndexJournalEntryData.Record> recordPredicate;

    public IndexJournalReaderSingleCompressedFile(Path file) throws IOException {
        fileHeader = readHeader(file);

        this.recordPredicate = null;
        this.entryPredicate = null;
    }

    public IndexJournalReaderSingleCompressedFile(Path file, Predicate<IndexJournalReadEntry> entryPredicate, Predicate<IndexJournalEntryData.Record> recordPredicate) throws IOException {
        journalFile = file;
        fileHeader = readHeader(file);

        var fileInputStream = Files.newInputStream(file, StandardOpenOption.READ);
        fileInputStream.skipNBytes(FILE_HEADER_SIZE_BYTES);

        this.recordPredicate = recordPredicate;
        this.entryPredicate = entryPredicate;
    }

    private static IndexJournalFileHeader readHeader(Path file) throws IOException {
        journalFile = file;

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

        return new DataInputStream(new ZstdInputStream(fileInputStream));
    }

    public IndexJournalFileHeader fileHeader() {
        return fileHeader;
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
    public IndexJournalStatistics getStatistics() {
        int highestWord = 0;

        // Docs cardinality is a candidate for a HyperLogLog
        Roaring64Bitmap docsBitmap = new Roaring64Bitmap();

        for (var entry : this) {
            var entryData = entry.readEntry();

            if (filter(entry)) {
                docsBitmap.addLong(entry.docId() & 0x0000_0000_FFFF_FFFFL);

                for (var item : entryData) {
                    if (filter(entry, item)) {
                        highestWord = Integer.max(item.wordId(), highestWord);
                    }
                }
            }
        }

        return new IndexJournalStatistics(highestWord, docsBitmap.getIntCardinality());
    }

    @Override
    public void forEachWordId(IntConsumer consumer) {
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
    public void forEachUrlIdWordId(BiIntConsumer consumer) {
        for (var entry : this) {
            var data = entry.readEntry();

            for (var post : data) {
                if (filter(entry, post)) {
                    consumer.accept(entry.urlId(), post.wordId());
                }
            }
        }
    }

    @Override
    public void forEachDocIdWordId(LongIntConsumer consumer) {
        for (var entry : this) {
            var data = entry.readEntry();

            for (var post : data) {
                if (filter(entry, post)) {
                    consumer.accept(entry.docId(), post.wordId());
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
    public void forEachUrlId(IntConsumer consumer) {
        for (var entry : this) {
            if (filter(entry)) {
                consumer.accept(entry.urlId());
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
