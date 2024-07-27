package nu.marginalia.index.journal;

import lombok.SneakyThrows;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.column.array.ByteArrayColumnWriter;
import nu.marginalia.slop.column.dynamic.GammaCodedSequenceWriter;
import nu.marginalia.slop.column.primitive.ByteColumnWriter;
import nu.marginalia.slop.column.primitive.IntColumnWriter;
import nu.marginalia.slop.column.primitive.LongColumnWriter;
import nu.marginalia.slop.desc.SlopTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class IndexJournalSlopWriter extends SlopTable {

    private final IntColumnWriter featuresWriter;
    private final IntColumnWriter sizeWriter;
    private final LongColumnWriter combinedIdWriter;
    private final LongColumnWriter documentMetaWriter;

    private final LongColumnWriter termCountsWriter;
    private final LongColumnWriter termIdsWriter;
    private final ByteColumnWriter termMetadataWriter;
    private final GammaCodedSequenceWriter termPositionsWriter;

    private final GammaCodedSequenceWriter spansWriter;
    private final ByteArrayColumnWriter spanCodesWriter;

    private static final MurmurHash3_128 hash = new MurmurHash3_128();

    public IndexJournalSlopWriter(Path dir, int page) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }


        featuresWriter = IndexJournalPage.features.forPage(page).create(this, dir);
        sizeWriter = IndexJournalPage.size.forPage(page).create(this, dir);

        combinedIdWriter = IndexJournalPage.combinedId.forPage(page).create(this, dir);
        documentMetaWriter = IndexJournalPage.documentMeta.forPage(page).create(this, dir);

        termCountsWriter = IndexJournalPage.termCounts.forPage(page).create(this, dir);

        termIdsWriter = IndexJournalPage.termIds.forPage(page).create(this.columnGroup("keywords"), dir);
        termMetadataWriter = IndexJournalPage.termMeta.forPage(page).create(this.columnGroup("keywords"), dir);
        termPositionsWriter = IndexJournalPage.positions.forPage(page).create(this.columnGroup("keywords"), dir);

        spansWriter = IndexJournalPage.spans.forPage(page).create(this.columnGroup("spans"), dir);
        spanCodesWriter = IndexJournalPage.spanCodes.forPage(page).create(this.columnGroup("spans"), dir);
    }

    @SneakyThrows
    public void put(long combinedId, SlopDocumentRecord.KeywordsProjection keywordsProjection) {

        combinedIdWriter.put(combinedId);
        featuresWriter.put(keywordsProjection.htmlFeatures());
        sizeWriter.put(keywordsProjection.length());
        documentMetaWriter.put(keywordsProjection.documentMetadata());

        // -- write keyword data --

        final List<String> keywords = keywordsProjection.words();
        byte[] termMetadata = keywordsProjection.metas();

        termCountsWriter.put(keywords.size());

        // termIds are the special hashes of the keywords
        long[] termIds = new long[keywordsProjection.words().size()];
        for (int i = 0; i < termIds.length; i++) {
            termIds[i] = hash.hashKeyword(keywords.get(i));
        }

        List<CodedSequence> termPositions = keywordsProjection.positions();
        for (int i = 0; i < termMetadata.length; i++) {
            termMetadataWriter.put(termMetadata[i]);
            termIdsWriter.put(termIds[i]);
            termPositionsWriter.put((GammaCodedSequence) termPositions.get(i));
        }

        // -- write spans --

        spanCodesWriter.put(keywordsProjection.spanCodes());
        for (var span : keywordsProjection.spans()) {
            spansWriter.put((GammaCodedSequence) span);
        }
    }

    public void close() throws IOException {
        featuresWriter.close();
        sizeWriter.close();
        combinedIdWriter.close();
        documentMetaWriter.close();
        termCountsWriter.close();
        termIdsWriter.close();
        termMetadataWriter.close();
        termPositionsWriter.close();
        spansWriter.close();
        spanCodesWriter.close();
    }
}
