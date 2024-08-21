package nu.marginalia.model.processed;

import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.string.TxtStringColumn;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public record SlopDomainLinkRecord(
        String source,
        String dest)
{
    private static final TxtStringColumn sourcesColumn = new TxtStringColumn("source", StorageType.GZIP);
    private static final TxtStringColumn destsColumn = new TxtStringColumn("dest", StorageType.GZIP);

    public static Reader reader(Path baseDir, int page) throws IOException {
        return new Reader(baseDir, page);
    }

    public static class Reader extends SlopTable {
        private final TxtStringColumn.Reader sourcesReader;
        private final TxtStringColumn.Reader destsReader;

        public Reader(SlopTable.Ref<SlopDomainLinkRecord> ref) throws IOException {
            super(ref);

            sourcesReader = sourcesColumn.open(this);
            destsReader = destsColumn.open(this);
        }

        public Reader(Path baseDir, int page) throws IOException {
            this(new Ref<>(baseDir, page));
        }

        public boolean hasMore() throws IOException {
            return sourcesReader.hasRemaining();
        }

        public void forEach(BiConsumer<String /* source */, String /* dest */> recordConsumer) throws IOException {
            while (hasMore()) {
                recordConsumer.accept(sourcesReader.get(), destsReader.get());
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
        private final TxtStringColumn.Writer sourcesWriter;
        private final TxtStringColumn.Writer destsWriter;

        public Writer(Path baseDir, int page) throws IOException {
            super(baseDir, page);

            sourcesWriter = sourcesColumn.create(this);
            destsWriter = destsColumn.create(this);
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
