package nu.marginalia.util.btree;

import nu.marginalia.util.array.LongArray;

import java.io.IOException;

public interface WriteCallback {
    void write(LongArray slice) throws IOException;
}
