package nu.marginalia.util.btree;

import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.btree.model.BTreeHeader;

public interface BTreeReaderIf {
    BTreeHeader getHeader();

    int numEntries();

    void retainEntries(LongQueryBuffer buffer);

    void rejectEntries(LongQueryBuffer buffer);

    long findEntry(long keyRaw);

    void readData(long[] data, int n, long pos);

    long[] queryData(long[] urls, int offset);

}
