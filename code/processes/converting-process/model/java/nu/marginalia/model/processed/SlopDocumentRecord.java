package nu.marginalia.model.processed;

import lombok.Builder;
import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.sequence.slop.GammaCodedSequenceArrayColumn;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.array.ObjectArrayColumn;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.column.primitive.FloatColumn;
import nu.marginalia.slop.column.primitive.IntColumn;
import nu.marginalia.slop.column.primitive.LongColumn;
import nu.marginalia.slop.column.string.EnumColumn;
import nu.marginalia.slop.column.string.StringColumn;
import nu.marginalia.slop.column.string.TxtStringColumn;
import nu.marginalia.slop.desc.StorageType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record SlopDocumentRecord(
        String domain,
        String url,
        int ordinal,
        String state,
        String stateReason,
        String title,
        String description,
        int htmlFeatures,
        String htmlStandard,
        int length,
        long hash,
        float quality,
        long documentMetadata,
        Integer pubYear,
        List<String> words,
        byte[] metas,
        List<GammaCodedSequence> positions,
        byte[] spanCodes,
        List<GammaCodedSequence> spans
) {

    public SlopDocumentRecord {
        if (spanCodes.length != spans.size())
            throw new IllegalArgumentException("Span codes and spans must have the same length");
        if (metas.length != words.size() || metas.length != positions.size())
            throw new IllegalArgumentException("Metas, words and positions must have the same length");
    }

    @Builder
    public record KeywordsProjection(
            String domain,
            int ordinal,
            int htmlFeatures,
            long documentMetadata,
            int length,
            List<String> words,
            byte[] metas,
            List<GammaCodedSequence> positions,
            byte[] spanCodes,
            List<GammaCodedSequence> spans)
    {
        // Override the equals method since records don't generate default equals that deal with array fields properly
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof KeywordsProjection that)) return false;

            return length == that.length && ordinal == that.ordinal && htmlFeatures == that.htmlFeatures && documentMetadata == that.documentMetadata && Arrays.equals(metas, that.metas) && Objects.equals(domain, that.domain) && Arrays.equals(spanCodes, that.spanCodes) && Objects.equals(words, that.words) && Objects.equals(spans, that.spans) && Objects.equals(positions, that.positions);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(domain);
            result = 31 * result + ordinal;
            result = 31 * result + htmlFeatures;
            result = 31 * result + Long.hashCode(documentMetadata);
            result = 31 * result + length;
            result = 31 * result + Objects.hashCode(words);
            result = 31 * result + Arrays.hashCode(metas);
            result = 31 * result + Objects.hashCode(positions);
            result = 31 * result + Arrays.hashCode(spanCodes);
            result = 31 * result + Objects.hashCode(spans);
            return result;
        }
    }

    public record MetadataProjection(
            String domain,
            String url,
            int ordinal,
            String title,
            String description,
            int htmlFeatures,
            String htmlStandard,
            int length,
            long hash,
            float quality,
            Integer pubYear
    ) {

    }

    // Basic information
    private static final TxtStringColumn domainsColumn = new TxtStringColumn("domain", StorageType.GZIP);
    private static final TxtStringColumn urlsColumn = new TxtStringColumn("url", StorageType.GZIP);
    private static final VarintColumn ordinalsColumn = new VarintColumn("ordinal", StorageType.PLAIN);
    private static final EnumColumn statesColumn = new EnumColumn("state", StorageType.PLAIN);
    private static final StringColumn stateReasonsColumn = new StringColumn("stateReason", StorageType.GZIP);

    // Document metadata
    private static final StringColumn titlesColumn = new StringColumn("title", StorageType.GZIP);
    private static final StringColumn descriptionsColumn = new StringColumn("description", StorageType.GZIP);
    private static final EnumColumn htmlStandardsColumn = new EnumColumn("htmlStandard", StorageType.PLAIN);
    private static final IntColumn htmlFeaturesColumn = new IntColumn("htmlFeatures", StorageType.PLAIN);
    private static final IntColumn lengthsColumn = new IntColumn("length", StorageType.PLAIN);
    private static final IntColumn pubYearColumn = new IntColumn("pubYear", StorageType.PLAIN);
    private static final LongColumn hashesColumn = new LongColumn("hash", StorageType.PLAIN);
    private static final FloatColumn qualitiesColumn = new FloatColumn("quality", StorageType.PLAIN);
    private static final LongColumn domainMetadata = new LongColumn("domainMetadata", StorageType.PLAIN);

    // Keyword-level columns, these are enumerated by the counts column

    private static final ObjectArrayColumn<String> keywordsColumn = new StringColumn("keywords", StorageType.ZSTD).asArray();
    private static final ByteArrayColumn termMetaColumn = new ByteArrayColumn("termMetadata", StorageType.ZSTD);
    private static final GammaCodedSequenceArrayColumn termPositionsColumn = new GammaCodedSequenceArrayColumn("termPositions", StorageType.ZSTD);

    // Spans columns

    private static final ByteArrayColumn spanCodesColumn = new ByteArrayColumn("spanCodes", StorageType.ZSTD);
    private static final GammaCodedSequenceArrayColumn spansColumn = new GammaCodedSequenceArrayColumn("spans", StorageType.ZSTD);

    public static class KeywordsProjectionReader extends SlopTable {
        private final TxtStringColumn.Reader domainsReader;
        private final VarintColumn.Reader ordinalsReader;
        private final IntColumn.Reader htmlFeaturesReader;
        private final LongColumn.Reader domainMetadataReader;
        private final IntColumn.Reader lengthsReader;

        private final ObjectArrayColumn<String>.Reader keywordsReader;
        private final ByteArrayColumn.Reader termMetaReader;
        private final GammaCodedSequenceArrayColumn.Reader termPositionsReader;

        private final ByteArrayColumn.Reader spanCodesReader;
        private final GammaCodedSequenceArrayColumn.Reader spansReader;

        public KeywordsProjectionReader(SlopPageRef<SlopDocumentRecord> pageRef) throws IOException {
            this(pageRef.baseDir(), pageRef.page());
        }

        public KeywordsProjectionReader(Path baseDir, int page) throws IOException {
            super(page);
            domainsReader = domainsColumn.open(this, baseDir);
            ordinalsReader = ordinalsColumn.open(this, baseDir);
            htmlFeaturesReader = htmlFeaturesColumn.open(this, baseDir);
            domainMetadataReader = domainMetadata.open(this, baseDir);
            lengthsReader = lengthsColumn.open(this, baseDir);

            keywordsReader = keywordsColumn.open(this, baseDir);
            termMetaReader = termMetaColumn.open(this, baseDir);
            termPositionsReader = termPositionsColumn.open(this, baseDir);

            spanCodesReader = spanCodesColumn.open(this, baseDir);
            spansReader = spansColumn.open(this, baseDir);
        }

        public boolean hasMore() throws IOException {
            return domainsReader.hasRemaining();
        }

        @Nullable
        public KeywordsProjection next() throws IOException {
            String domain = domainsReader.get();
            int ordinal = ordinalsReader.get();
            int htmlFeatures = htmlFeaturesReader.get();
            long documentMetadata = domainMetadataReader.get();
            int length = lengthsReader.get();

            List<String> words = keywordsReader.get();
            List<GammaCodedSequence> positions = termPositionsReader.get();
            byte[] metas = termMetaReader.get();
            byte[] spanCodes = spanCodesReader.get();
            List<GammaCodedSequence> spans = spansReader.get();

            return new KeywordsProjection(
                    domain,
                    ordinal,
                    htmlFeatures,
                    documentMetadata,
                    length,
                    words,
                    metas,
                    positions,
                    spanCodes,
                    spans
            );
        }

    }

    public static class MetadataReader extends SlopTable {
        private final TxtStringColumn.Reader domainsReader;
        private final TxtStringColumn.Reader urlsReader;
        private final VarintColumn.Reader ordinalsReader;
        private final StringColumn.Reader titlesReader;
        private final StringColumn.Reader descriptionsReader;

        private final IntColumn.Reader htmlFeaturesReader;
        private final EnumColumn.Reader htmlStandardsReader;
        private final IntColumn.Reader lengthsReader;
        private final LongColumn.Reader hashesReader;
        private final FloatColumn.Reader qualitiesReader;
        private final IntColumn.Reader pubYearReader;

        public MetadataReader(SlopPageRef<SlopDocumentRecord> pageRef) throws IOException{
            this(pageRef.baseDir(), pageRef.page());
        }

        public MetadataReader(Path baseDir, int page) throws IOException {
            super(page);

            this.domainsReader = domainsColumn.open(this, baseDir);
            this.urlsReader = urlsColumn.open(this, baseDir);
            this.ordinalsReader = ordinalsColumn.open(this, baseDir);
            this.titlesReader = titlesColumn.open(this, baseDir);
            this.descriptionsReader = descriptionsColumn.open(this, baseDir);
            this.htmlFeaturesReader = htmlFeaturesColumn.open(this, baseDir);
            this.htmlStandardsReader = htmlStandardsColumn.open(this, baseDir);
            this.lengthsReader = lengthsColumn.open(this, baseDir);
            this.hashesReader = hashesColumn.open(this, baseDir);
            this.qualitiesReader = qualitiesColumn.open(this, baseDir);
            this.pubYearReader = pubYearColumn.open(this, baseDir);
        }

        public boolean hasMore() throws IOException {
            return domainsReader.hasRemaining();
        }

        public MetadataProjection next() throws IOException {
            int pubYear = pubYearReader.get();
            return new MetadataProjection(
                    domainsReader.get(),
                    urlsReader.get(),
                    ordinalsReader.get(),
                    titlesReader.get(),
                    descriptionsReader.get(),
                    htmlFeaturesReader.get(),
                    htmlStandardsReader.get(),
                    lengthsReader.get(),
                    hashesReader.get(),
                    qualitiesReader.get(),
                    pubYear < 0 ? null : pubYear
            );
        }

    }

    public static class Writer extends SlopTable {
        private final TxtStringColumn.Writer domainsWriter;
        private final TxtStringColumn.Writer urlsWriter;
        private final VarintColumn.Writer ordinalsWriter;
        private final EnumColumn.Writer statesWriter;
        private final StringColumn.Writer stateReasonsWriter;
        private final StringColumn.Writer titlesWriter;
        private final StringColumn.Writer descriptionsWriter;
        private final IntColumn.Writer htmlFeaturesWriter;
        private final EnumColumn.Writer htmlStandardsWriter;
        private final IntColumn.Writer lengthsWriter;
        private final LongColumn.Writer hashesWriter;
        private final FloatColumn.Writer qualitiesWriter;
        private final LongColumn.Writer domainMetadataWriter;
        private final IntColumn.Writer pubYearWriter;
        private final ObjectArrayColumn<String>.Writer keywordsWriter;
        private final ByteArrayColumn.Writer termMetaWriter;
        private final GammaCodedSequenceArrayColumn.Writer termPositionsWriter;
        private final ByteArrayColumn.Writer spansCodesWriter;
        private final GammaCodedSequenceArrayColumn.Writer spansWriter;

        public Writer(Path baseDir, int page) throws IOException {
            super(page);

            domainsWriter = domainsColumn.create(this, baseDir);
            urlsWriter = urlsColumn.create(this, baseDir);
            ordinalsWriter = ordinalsColumn.create(this, baseDir);
            statesWriter = statesColumn.create(this, baseDir);
            stateReasonsWriter = stateReasonsColumn.create(this, baseDir);
            titlesWriter = titlesColumn.create(this, baseDir);
            descriptionsWriter = descriptionsColumn.create(this, baseDir);
            htmlFeaturesWriter = htmlFeaturesColumn.create(this, baseDir);
            htmlStandardsWriter = htmlStandardsColumn.create(this, baseDir);
            lengthsWriter = lengthsColumn.create(this, baseDir);
            hashesWriter = hashesColumn.create(this, baseDir);
            qualitiesWriter = qualitiesColumn.create(this, baseDir);
            domainMetadataWriter = domainMetadata.create(this, baseDir);
            pubYearWriter = pubYearColumn.create(this, baseDir);

            keywordsWriter = keywordsColumn.create(this, baseDir);
            termMetaWriter = termMetaColumn.create(this, baseDir);
            termPositionsWriter = termPositionsColumn.create(this, baseDir);

            spansCodesWriter = spanCodesColumn.create(this, baseDir);
            spansWriter = spansColumn.create(this, baseDir);
        }

        public void write(SlopDocumentRecord record) throws IOException {
            domainsWriter.put(record.domain());
            urlsWriter.put(record.url());
            ordinalsWriter.put(record.ordinal());
            statesWriter.put(record.state());
            stateReasonsWriter.put(record.stateReason());
            titlesWriter.put(record.title());
            descriptionsWriter.put(record.description());
            htmlFeaturesWriter.put(record.htmlFeatures());
            htmlStandardsWriter.put(record.htmlStandard());
            lengthsWriter.put(record.length());
            hashesWriter.put(record.hash());
            qualitiesWriter.put(record.quality());
            domainMetadataWriter.put(record.documentMetadata());

            if (record.pubYear == null) {
                pubYearWriter.put(-1);
            } else {
                pubYearWriter.put(record.pubYear());
            }

            keywordsWriter.put(record.words());
            termMetaWriter.put(record.metas());
            termPositionsWriter.put(record.positions());
            spansCodesWriter.put(record.spanCodes());
            spansWriter.put(record.spans());
        }
    }
}
