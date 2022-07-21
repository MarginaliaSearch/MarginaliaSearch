package nu.marginalia.wmsa.edge.index.conversion.words;

import nu.marginalia.util.btree.BTreeWriter;
import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.multimap.MultimapFileLongSlice;
import nu.marginalia.wmsa.edge.index.reader.IndexWordsTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import static nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter.urlsBTreeContext;

public class WordsTableWriter {
    private final WordIndexTables table;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final BTreeContext wordsBTreeContext = new BTreeContext(7, 2, 0x0000_0000_FFFF_FFFFL, 8);

    public WordsTableWriter(int length) {
        table = new WordIndexTables(length);
    }

    public void acceptWord(int wordId) {
        table.lengths().increment(wordId);
    }

    public WordIndexOffsetsTable getTable() {
        return table.offsets();
    }

    public void write(File file) throws IOException {
        table.convert();

        logger.info("Writing table - {} max", table.offsets().numberOfUsedWords);

        final int tableSize = table.offsets().numberOfUsedWords;

        try (var mmf = MultimapFileLong.forOutput(file.toPath(), tableSize/8L)) {
            mmf.put(0, IndexWordsTable.Strategy.BTREE.ordinal());
            long offset = 1;

            var writer = new BTreeWriter(mmf, wordsBTreeContext);

            writer.write(offset, tableSize, this::writeBTreeDataBlock);
        }
    }

    private void writeBTreeDataBlock(MultimapFileLongSlice mapSlice) {
        long urlFileOffset = 0;
        int idx = 0;

        var offsetTable = table.offsets().table;

        if (offsetTable[0] != 0) {
            int length = (int) offsetTable[0];
            mapSlice.put(idx++, (long)length<<32);
            mapSlice.put(idx++, 0);

            urlFileOffset += (urlsBTreeContext.calculateSize(length));
        }

        for (int i = 1; i < offsetTable.length; i++) {
            final int length = (int)(offsetTable[i] - offsetTable[i-1]);

            if (length > 0) {
                mapSlice.put(idx++, (long)length << 32 | i);
                mapSlice.put(idx++, urlFileOffset);

                urlFileOffset += (urlsBTreeContext.calculateSize(length));
            }
        }
    }
}
