package nu.marginalia.index.journal;

import lombok.SneakyThrows;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.sequence.slop.GammaCodedSequenceArrayWriter;
import nu.marginalia.slop.column.array.ByteArrayColumnWriter;
import nu.marginalia.slop.column.array.LongArrayColumnWriter;
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

    private final LongArrayColumnWriter termIdsWriter;
    private final ByteArrayColumnWriter termMetadataWriter;
    private final GammaCodedSequenceArrayWriter termPositionsWriter;

    private final GammaCodedSequenceArrayWriter spansWriter;
    private final ByteArrayColumnWriter spanCodesWriter;

    private static final MurmurHash3_128 hash = new MurmurHash3_128();

    public IndexJournalSlopWriter(Path dir, int page) throws IOException {

        super(page);

        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }

        featuresWriter = IndexJournalPage.features.create(this, dir);
        sizeWriter = IndexJournalPage.size.create(this, dir);

        combinedIdWriter = IndexJournalPage.combinedId.create(this, dir);
        documentMetaWriter = IndexJournalPage.documentMeta.create(this, dir);

        termIdsWriter = IndexJournalPage.termIds.create(this, dir);
        termMetadataWriter = IndexJournalPage.termMeta.create(this, dir);
        termPositionsWriter = IndexJournalPage.positions.create(this, dir);

        spanCodesWriter = IndexJournalPage.spanCodes.create(this, dir);
        spansWriter = IndexJournalPage.spans.create(this, dir);
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
