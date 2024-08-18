package nu.marginalia.index.journal;

import lombok.SneakyThrows;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.sequence.slop.GammaCodedSequenceArrayColumn;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.array.LongArrayColumn;
import nu.marginalia.slop.column.primitive.IntColumn;
import nu.marginalia.slop.column.primitive.LongColumn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class IndexJournalSlopWriter extends SlopTable {

    private final IntColumn.Writer featuresWriter;
    private final IntColumn.Writer sizeWriter;
    private final LongColumn.Writer combinedIdWriter;
    private final LongColumn.Writer documentMetaWriter;

    private final LongArrayColumn.Writer termIdsWriter;
    private final ByteArrayColumn.Writer termMetadataWriter;
    private final GammaCodedSequenceArrayColumn.Writer termPositionsWriter;

    private final GammaCodedSequenceArrayColumn.Writer spansWriter;
    private final ByteArrayColumn.Writer spanCodesWriter;

    private static final MurmurHash3_128 hash = new MurmurHash3_128();

    public IndexJournalSlopWriter(Path dir, int page) throws IOException {

        super(dir, page);

        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }

        featuresWriter = IndexJournalPage.features.create(this);
        sizeWriter = IndexJournalPage.size.create(this);

        combinedIdWriter = IndexJournalPage.combinedId.create(this);
        documentMetaWriter = IndexJournalPage.documentMeta.create(this);

        termIdsWriter = IndexJournalPage.termIds.create(this);
        termMetadataWriter = IndexJournalPage.termMeta.create(this);
        termPositionsWriter = IndexJournalPage.positions.create(this);

        spanCodesWriter = IndexJournalPage.spanCodes.create(this);
        spansWriter = IndexJournalPage.spans.create(this);
    }

    @SneakyThrows
    public void put(long combinedId, SlopDocumentRecord.KeywordsProjection keywordsProjection) {

        combinedIdWriter.put(combinedId);
        featuresWriter.put(keywordsProjection.htmlFeatures());
        sizeWriter.put(keywordsProjection.length());
        documentMetaWriter.put(keywordsProjection.documentMetadata());

        // -- write keyword data --

        final List<String> keywords = keywordsProjection.words();

        // termIds are the special hashes of the keywords
        long[] termIds = new long[keywordsProjection.words().size()];
        for (int i = 0; i < termIds.length; i++) {
            termIds[i] = hash.hashKeyword(keywords.get(i));
        }

        termIdsWriter.put(termIds);
        termPositionsWriter.put(keywordsProjection.positions());
        termMetadataWriter.put(keywordsProjection.metas());

        // -- write spans --

        spanCodesWriter.put(keywordsProjection.spanCodes());
        spansWriter.put(keywordsProjection.spans());
    }

    public void close() throws IOException {
        featuresWriter.close();
        sizeWriter.close();
        combinedIdWriter.close();
        documentMetaWriter.close();
        termIdsWriter.close();
        termMetadataWriter.close();
        termPositionsWriter.close();
        spansWriter.close();
        spanCodesWriter.close();
    }
}
