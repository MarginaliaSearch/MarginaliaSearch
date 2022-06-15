package nu.marginalia.wmsa.edge.index.conversion.words;

/** Contains a stateful table of word index offsets, initially in lengths mode
 * where the table contains how many postings exist for each word; then in offsets
 * mode, where the lengths are converted into the necessary offsets for each block
 * of document data.
 *
 * Caveat! This uses the same underlying array to conserve space.
 *
 */
public class WordIndexTables {
    private WordIndexLengthsTable lengthsTable;
    private WordIndexOffsetsTable offsetsTable;

    private boolean converted = false;

    public WordIndexTables(int size) {
        lengthsTable = new WordIndexLengthsTable(size);
    }

    public WordIndexLengthsTable lengths() {
        if (converted) throw new IllegalStateException("Table has been converted");

        return lengthsTable;
    }

    public WordIndexOffsetsTable offsets() {
        if (!converted) throw new IllegalStateException("Table has not been converted");

        return offsetsTable;
    }

    public void convert() {
        if (converted) throw new IllegalStateException("Table has been converted");

        // Go from lengths to offsets, i.e.
        // BEFORE: 1, 2, 1, 3, 0, 2
        // AFTER:  1, 3, 4, 7, 7, 9

        long[] table = lengthsTable.table;
        int numberOfUsedWords = 0;

        if (table[0] != 0) numberOfUsedWords = 1;

        for (int i = 1; i < table.length; i++) {
            if (table[i] != 0) {
                numberOfUsedWords++;
            }
            table[i] += table[i-1];
        }

        lengthsTable = null;
        offsetsTable = new WordIndexOffsetsTable(table, numberOfUsedWords);
        converted = true;
    }
}
