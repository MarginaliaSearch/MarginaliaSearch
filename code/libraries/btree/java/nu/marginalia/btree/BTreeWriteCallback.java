package nu.marginalia.btree;

import nu.marginalia.array.LongArray;

import java.io.IOException;

public interface BTreeWriteCallback {
    void write(LongArray slice) throws IOException;
}
