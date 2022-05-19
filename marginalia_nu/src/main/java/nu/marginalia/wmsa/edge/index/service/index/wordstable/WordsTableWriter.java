package nu.marginalia.wmsa.edge.index.service.index.wordstable;

import nu.marginalia.util.btree.BTreeWriter;
import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import static nu.marginalia.wmsa.edge.index.service.index.SearchIndexConverter.urlsBTreeContext;

public class WordsTableWriter {
    private final long[] table;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final BTreeContext wordsBTreeContext = new BTreeContext(7, 2, 0x0000_0000_FFFF_FFFFL, 8);

    public WordsTableWriter(int length) {
        table = new long[length];
    }

    public void acceptWord(int wordId) {
        if (wordId >= table.length) {
            logger.warn("Invalid word-id {}", wordId);
        }
        else {
            table[wordId]++;
        }
    }

    public long[] getTable() {
        return table;
    }
    public void write(File file) throws Exception {

        int tableSize = 0;

        if (table[0] != 0) tableSize = 1;

        for (int i = 1; i < table.length; i++) {
            if (table[i] != 0) {
                tableSize++;
            }
            table[i] += table[i-1];
        }

        logger.info("Writing table {} words {} max", tableSize, table.length);

        writeBtreeWordsFile(file, table, tableSize);

    }

    private void writeBtreeWordsFile(File outputFileWords, long[] table, int tableSize) throws Exception {
        try (var mmf = MultimapFileLong.forOutput(outputFileWords.toPath(), tableSize/8L)) {
            mmf.put(0, IndexWordsTable.Strategy.BTREE.ordinal());
            long offset = 1;

            var writer = new BTreeWriter(mmf, wordsBTreeContext);

            writer.write(offset, tableSize, (idx) -> {
                long urlFileOffset = 0;

                if (table[0] != 0) {
                    int length = (int) table[0];
                    mmf.put(idx++, (long)length<<32);
                    mmf.put(idx++, 0);

                    urlFileOffset += (urlsBTreeContext.calculateSize(length));
                }

                for (int i = 1; i < table.length; i++) {
                    if (table[i] != table[i - 1]) {
                        int length = (int)(table[i] - table[i-1]);
                        mmf.put(idx++, (long)length << 32 | i);
                        mmf.put(idx++, urlFileOffset);

                        urlFileOffset += (urlsBTreeContext.calculateSize(length));
                    }
                }
            });
        }
    }

}
