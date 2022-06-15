package nu.marginalia.wmsa.edge.index.conversion.words;

public class WordIndexLengthsTable {
    final long[] table;

    public WordIndexLengthsTable(int size) {
        this.table = new long[size];
    }
    public void increment(int idx) { table[idx]++; }
}
