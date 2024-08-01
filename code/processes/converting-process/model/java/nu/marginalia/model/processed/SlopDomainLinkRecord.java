package nu.marginalia.model.processed;

import nu.marginalia.slop.column.string.StringColumnReader;
import nu.marginalia.slop.column.string.StringColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.SlopTable;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public record SlopDomainLinkRecord(
        String source,
        String dest)
{
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> sourcesColumn = new ColumnDesc<>("source", ColumnType.TXTSTRING, StorageType.GZIP);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> destsColumn = new ColumnDesc<>("dest", ColumnType.TXTSTRING, StorageType.GZIP);

    public static Reader reader(Path baseDir, int page) throws IOException {
        return new Reader(baseDir, page);
    }

    public static class Reader extends SlopTable {
        private final StringColumnReader sourcesReader;
        private final StringColumnReader destsReader;

        public Reader(SlopPageRef<SlopDomainLinkRecord> page) throws IOException {
            this(page.baseDir(), page.page());
        }

        public Reader(Path baseDir, int page) throws IOException {
            sourcesReader = sourcesColumn.forPage(page).open(this, baseDir);
            destsReader = destsColumn.forPage(page).open(this, baseDir);
        }

        public boolean hasMore() throws IOException {
            return sourcesReader.hasRemaining();
        }

        public void forEach(Consumer<SlopDomainLinkRecord> recordConsumer) throws IOException {
            while (hasMore()) {
                recordConsumer.accept(next());
            }
        }

        public SlopDomainLinkRecord next() throws IOException {

            return new SlopDomainLinkRecord(
                    sourcesReader.get(),
                    destsReader.get()
            );
        }
    }

    public static class Writer extends SlopTable {
        private final StringColumnWriter sourcesWriter;
        private final StringColumnWriter destsWriter;

        public Writer(Path baseDir, int page) throws IOException {
            sourcesWriter = sourcesColumn.forPage(page).create(this, baseDir);
            destsWriter = destsColumn.forPage(page).create(this, baseDir);
        }

        public void write(SlopDomainLinkRecord record) throws IOException {
            sourcesWriter.put(record.source());
            destsWriter.put(record.dest());
        }
        
        @Override
        public void close() throws IOException {
            sourcesWriter.close();
            destsWriter.close();
        }
    }
}
