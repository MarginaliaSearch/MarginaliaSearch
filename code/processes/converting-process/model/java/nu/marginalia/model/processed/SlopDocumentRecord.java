package nu.marginalia.model.processed;

import lombok.Builder;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.column.array.ByteArrayColumnReader;
import nu.marginalia.slop.column.array.ByteArrayColumnWriter;
import nu.marginalia.slop.column.dynamic.GammaCodedSequenceReader;
import nu.marginalia.slop.column.dynamic.GammaCodedSequenceWriter;
import nu.marginalia.slop.column.dynamic.VarintColumnReader;
import nu.marginalia.slop.column.dynamic.VarintColumnWriter;
import nu.marginalia.slop.column.primitive.*;
import nu.marginalia.slop.column.string.StringColumnReader;
import nu.marginalia.slop.column.string.StringColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        List<CodedSequence> positions,
        byte[] spanCodes,
        List<CodedSequence> spans
) {

    /** Constructor for partial records */
    public SlopDocumentRecord(String domain,
                              String url,
                              int ordinal,
                              String state,
                              String stateReason)
    {
        this(domain, url, ordinal, state, stateReason, "", "", 0, "", 0, 0L, 0.0f, 0L, null, List.of(), new byte[0], List.of(), new byte[0], List.of());
    }

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
            List<CodedSequence> positions,
            byte[] spanCodes,
            List<CodedSequence> spans)
    { }

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
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> domainsColumn = new ColumnDesc<>("domain", ColumnType.TXTSTRING, StorageType.GZIP);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> urlsColumn = new ColumnDesc<>("url", ColumnType.TXTSTRING, StorageType.GZIP);
    private static final ColumnDesc<VarintColumnReader, VarintColumnWriter> ordinalsColumn = new ColumnDesc<>("ordinal", ColumnType.VARINT_LE, StorageType.PLAIN);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> statesColumn = new ColumnDesc<>("state", ColumnType.ENUM_LE, StorageType.PLAIN);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> stateReasonsColumn = new ColumnDesc<>("stateReason", ColumnType.TXTSTRING, StorageType.GZIP);

    // Document metadata
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> titlesColumn = new ColumnDesc<>("title", ColumnType.STRING, StorageType.GZIP);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> descriptionsColumn = new ColumnDesc<>("description", ColumnType.STRING, StorageType.GZIP);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> htmlStandardsColumn = new ColumnDesc<>("htmlStandard", ColumnType.ENUM_LE, StorageType.GZIP);
    private static final ColumnDesc<IntColumnReader, IntColumnWriter> htmlFeaturesColumn = new ColumnDesc<>("htmlFeatures", ColumnType.INT_LE, StorageType.PLAIN);
    private static final ColumnDesc<IntColumnReader, IntColumnWriter> lengthsColumn = new ColumnDesc<>("length", ColumnType.INT_LE, StorageType.PLAIN);
    private static final ColumnDesc<IntColumnReader, IntColumnWriter> pubYearColumn = new ColumnDesc<>("pubYear", ColumnType.INT_LE, StorageType.PLAIN);
    private static final ColumnDesc<LongColumnReader, LongColumnWriter> hashesColumn = new ColumnDesc<>("hash", ColumnType.LONG_LE, StorageType.PLAIN);
    private static final ColumnDesc<FloatColumnReader, FloatColumnWriter> qualitiesColumn = new ColumnDesc<>("quality", ColumnType.FLOAT_LE, StorageType.PLAIN);
    private static final ColumnDesc<LongColumnReader, LongColumnWriter> domainMetadata = new ColumnDesc<>("domainMetadata", ColumnType.LONG_LE, StorageType.PLAIN);

    // Keyword-level columns, these are enumerated by the counts column
    private static final ColumnDesc<VarintColumnReader, VarintColumnWriter> termCountsColumn = new ColumnDesc<>("termCounts", ColumnType.VARINT_LE, StorageType.PLAIN);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> keywordsColumn = new ColumnDesc<>("keywords", ColumnType.STRING, StorageType.ZSTD);
    private static final ColumnDesc<ByteColumnReader, ByteColumnWriter> termMetaColumn = new ColumnDesc<>("termMetadata", ColumnType.BYTE, StorageType.ZSTD);
    private static final ColumnDesc<GammaCodedSequenceReader, GammaCodedSequenceWriter> termPositionsColumn = new ColumnDesc<>("termPositions", ColumnType.BYTE_ARRAY_GCS, StorageType.ZSTD);

    // Spans columns
    private static final ColumnDesc<ByteArrayColumnReader, ByteArrayColumnWriter> spanCodesColumn = new ColumnDesc<>("spanCodes", ColumnType.BYTE_ARRAY, StorageType.ZSTD);
    private static final ColumnDesc<GammaCodedSequenceReader, GammaCodedSequenceWriter> spansColumn = new ColumnDesc<>("spans", ColumnType.BYTE_ARRAY_GCS, StorageType.ZSTD);

    public static class KeywordsProjectionReader implements AutoCloseable {
        private final StringColumnReader domainsReader;
        private final VarintColumnReader ordinalsReader;
        private final IntColumnReader htmlFeaturesReader;
        private final LongColumnReader domainMetadataReader;
        private final IntColumnReader lengthsReader;
        private final StringColumnReader keywordsReader;
        private final VarintColumnReader termCountsReader;
        private final ByteColumnReader termMetaReader;
        private final GammaCodedSequenceReader termPositionsReader;

        private final ByteArrayColumnReader spanCodesReader;
        private final GammaCodedSequenceReader spansReader;

        private final ByteBuffer workBuffer = ByteBuffer.allocate(65536);

        public KeywordsProjectionReader(SlopPageRef<SlopDocumentRecord> pageRef) throws IOException {
            this(pageRef.baseDir(), pageRef.page());
        }

        public KeywordsProjectionReader(Path baseDir, int page) throws IOException {
            domainsReader = domainsColumn.forPage(page).open(baseDir);
            ordinalsReader = ordinalsColumn.forPage(page).open(baseDir);
            htmlFeaturesReader = htmlFeaturesColumn.forPage(page).open(baseDir);
            domainMetadataReader = domainMetadata.forPage(page).open(baseDir);
            lengthsReader = lengthsColumn.forPage(page).open(baseDir);
            keywordsReader = keywordsColumn.forPage(page).open(baseDir);
            termCountsReader = termCountsColumn.forPage(page).open(baseDir);
            termMetaReader = termMetaColumn.forPage(page).open(baseDir);
            termPositionsReader = termPositionsColumn.forPage(page).open(baseDir);
            spanCodesReader = spanCodesColumn.forPage(page).open(baseDir);
            spansReader = spansColumn.forPage(page).open(baseDir);
        }

        public boolean hasMore() throws IOException {
            return domainsReader.hasRemaining();
        }

        public KeywordsProjection next() throws IOException {
            String domain = domainsReader.get();
            int ordinal = (int) ordinalsReader.get();
            int htmlFeatures = htmlFeaturesReader.get();
            long documentMetadata = domainMetadataReader.get();
            int length = lengthsReader.get();
            List<String> words = new ArrayList<>();

            List<CodedSequence> positions = new ArrayList<>();

            int termCounts = (int) termCountsReader.get();
            byte[] metas = new byte[termCounts];

            for (int i = 0; i < termCounts; i++) {
                metas[i] = termMetaReader.get();
                words.add(keywordsReader.get());
                positions.add(termPositionsReader.get(workBuffer));
            }

            byte[] spanCodes = spanCodesReader.get();

            List<CodedSequence> spans = new ArrayList<>(spanCodes.length);
            for (int i = 0; i < spanCodes.length; i++) {
                spans.add(spansReader.get(workBuffer));
            }

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


        public void close() throws IOException {
            domainsReader.close();
            ordinalsReader.close();
            htmlFeaturesReader.close();
            domainMetadataReader.close();
            lengthsReader.close();
            keywordsReader.close();
            termMetaReader.close();
            termPositionsReader.close();
            spanCodesReader.close();
            spansReader.close();
        }
    }

    public static class MetadataReader implements AutoCloseable {
        private final StringColumnReader domainsReader;
        private final StringColumnReader urlsReader;
        private final VarintColumnReader ordinalsReader;
        private final StringColumnReader titlesReader;
        private final StringColumnReader descriptionsReader;
        private final IntColumnReader htmlFeaturesReader;
        private final StringColumnReader htmlStandardsReader;
        private final IntColumnReader lengthsReader;
        private final LongColumnReader hashesReader;
        private final FloatColumnReader qualitiesReader;
        private final IntColumnReader pubYearReader;

        public MetadataReader(SlopPageRef<SlopDocumentRecord> pageRef) throws IOException{
            this(pageRef.baseDir(), pageRef.page());
        }

        public MetadataReader(Path baseDir, int page) throws IOException {
            this.domainsReader = domainsColumn.forPage(page).open(baseDir);
            this.urlsReader = urlsColumn.forPage(page).open(baseDir);
            this.ordinalsReader = ordinalsColumn.forPage(page).open(baseDir);
            this.titlesReader = titlesColumn.forPage(page).open(baseDir);
            this.descriptionsReader = descriptionsColumn.forPage(page).open(baseDir);
            this.htmlFeaturesReader = htmlFeaturesColumn.forPage(page).open(baseDir);
            this.htmlStandardsReader = htmlStandardsColumn.forPage(page).open(baseDir);
            this.lengthsReader = lengthsColumn.forPage(page).open(baseDir);
            this.hashesReader = hashesColumn.forPage(page).open(baseDir);
            this.qualitiesReader = qualitiesColumn.forPage(page).open(baseDir);
            this.pubYearReader = pubYearColumn.forPage(page).open(baseDir);
        }

        public MetadataProjection next() throws IOException {
            int pubYear = pubYearReader.get();
            return new MetadataProjection(
                    domainsReader.get(),
                    urlsReader.get(),
                    (int) ordinalsReader.get(),
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

        public boolean hasNext() throws IOException {
            return domainsReader.hasRemaining();
        }

        public void close() throws IOException {
            domainsReader.close();
            urlsReader.close();
            ordinalsReader.close();
            titlesReader.close();
            descriptionsReader.close();
            htmlFeaturesReader.close();
            htmlStandardsReader.close();
            lengthsReader.close();
            hashesReader.close();
            qualitiesReader.close();
            pubYearReader.close();
        }
    }

    public static class Writer implements AutoCloseable {
        private final StringColumnWriter domainsWriter;
        private final StringColumnWriter urlsWriter;
        private final VarintColumnWriter ordinalsWriter;
        private final StringColumnWriter statesWriter;
        private final StringColumnWriter stateReasonsWriter;
        private final StringColumnWriter titlesWriter;
        private final StringColumnWriter descriptionsWriter;
        private final IntColumnWriter htmlFeaturesWriter;
        private final StringColumnWriter htmlStandardsWriter;
        private final IntColumnWriter lengthsWriter;
        private final LongColumnWriter hashesWriter;
        private final FloatColumnWriter qualitiesWriter;
        private final LongColumnWriter domainMetadataWriter;
        private final IntColumnWriter pubYearWriter;
        private final VarintColumnWriter termCountsWriter;
        private final StringColumnWriter keywordsWriter;
        private final ByteColumnWriter termMetaWriter;
        private final GammaCodedSequenceWriter termPositionsWriter;
        private final ByteArrayColumnWriter spansCodesWriter;
        private final GammaCodedSequenceWriter spansWriter;

        public Writer(Path baseDir, int page) throws IOException {
            domainsWriter = domainsColumn.forPage(page).create(baseDir);
            urlsWriter = urlsColumn.forPage(page).create(baseDir);
            ordinalsWriter = ordinalsColumn.forPage(page).create(baseDir);
            statesWriter = statesColumn.forPage(page).create(baseDir);
            stateReasonsWriter = stateReasonsColumn.forPage(page).create(baseDir);
            titlesWriter = titlesColumn.forPage(page).create(baseDir);
            descriptionsWriter = descriptionsColumn.forPage(page).create(baseDir);
            htmlFeaturesWriter = htmlFeaturesColumn.forPage(page).create(baseDir);
            htmlStandardsWriter = htmlStandardsColumn.forPage(page).create(baseDir);
            lengthsWriter = lengthsColumn.forPage(page).create(baseDir);
            hashesWriter = hashesColumn.forPage(page).create(baseDir);
            qualitiesWriter = qualitiesColumn.forPage(page).create(baseDir);
            domainMetadataWriter = domainMetadata.forPage(page).create(baseDir);
            pubYearWriter = pubYearColumn.forPage(page).create(baseDir);
            termCountsWriter = termCountsColumn.forPage(page).create(baseDir);
            keywordsWriter = keywordsColumn.forPage(page).create(baseDir);
            termMetaWriter = termMetaColumn.forPage(page).create(baseDir);
            termPositionsWriter = termPositionsColumn.forPage(page).create(baseDir);

            spansWriter = spansColumn.forPage(page).create(baseDir);
            spansCodesWriter = spanCodesColumn.forPage(page).create(baseDir);
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

            byte[] termMetadata = record.metas();
            List<String> keywords = record.words();
            List<CodedSequence> termPositions = record.positions();

            termCountsWriter.put(termMetadata.length);

            for (int i = 0; i < termMetadata.length; i++) {
                termMetaWriter.put(termMetadata[i]);
                keywordsWriter.put(keywords.get(i));

                termPositionsWriter.put((GammaCodedSequence) termPositions.get(i));
            }

            assert record.spanCodes().length == record.spans.size() : "Span codes and spans must have the same length";

            spansCodesWriter.put(record.spanCodes());
            for (var span : record.spans) {
                spansWriter.put((GammaCodedSequence) span);
            }

        }

        public void close() throws IOException {
            domainsWriter.close();
            urlsWriter.close();
            ordinalsWriter.close();
            statesWriter.close();
            stateReasonsWriter.close();
            titlesWriter.close();
            descriptionsWriter.close();
            htmlFeaturesWriter.close();
            htmlStandardsWriter.close();
            lengthsWriter.close();
            hashesWriter.close();
            qualitiesWriter.close();
            domainMetadataWriter.close();
            pubYearWriter.close();
            termCountsWriter.close();
            keywordsWriter.close();
            termMetaWriter.close();
            termPositionsWriter.close();

            spansCodesWriter.close();
            spansWriter.close();
        }
    }
}
