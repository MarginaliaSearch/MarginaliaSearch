package nu.marginalia.btree.legacy;

import nu.marginalia.array.LongArray;

import java.io.IOException;

public interface LegacyBTreeWriteCallback {
    void write(LongArray slice) throws IOException;
}
