package nu.marginalia.index.journal;

import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.sequence.slop.VarintCodedSequenceArrayColumn;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.array.LongArrayColumn;
import nu.marginalia.slop.column.primitive.IntColumn;
import nu.marginalia.slop.column.primitive.LongColumn;
import nu.marginalia.slop.column.string.EnumColumn;

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
    private final LongArrayColumn.Writer termMetadataWriter;
    private final VarintCodedSequenceArrayColumn.Writer termPositionsWriter;

    private final VarintCodedSequenceArrayColumn.Writer spansWriter;
    private final ByteArrayColumn.Writer spanCodesWriter;
    private final EnumColumn.Writer languagesWriter;

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

        languagesWriter = IndexJournalPage.languageIsoCode.create(this);
    }

    public void put(long combinedId, SlopDocumentRecord.KeywordsProjection keywordsProjection, KeywordHasher hasher) throws IOException {

        combinedIdWriter.put(combinedId);
        featuresWriter.put(keywordsProjection.htmlFeatures());
        sizeWriter.put(keywordsProjection.length());
        documentMetaWriter.put(keywordsProjection.documentMetadata());
        languagesWriter.put(keywordsProjection.languageIsoCode());

        // -- write keyword data --

        final List<String> keywords = keywordsProjection.words();

        // termIds are the special hashes of the keywords
        long[] termIds = new long[keywordsProjection.words().size()];
        for (int i = 0; i < termIds.length; i++) {
            termIds[i] = hasher.hashKeyword(keywords.get(i));
        }

        termIdsWriter.put(termIds);
        termPositionsWriter.put(keywordsProjection.positions());
        termMetadataWriter.put(keywordsProjection.metas()); // FIXME

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
        languagesWriter.close();
        spansWriter.close();
        spanCodesWriter.close();
    }
}
