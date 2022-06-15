package nu.marginalia.util.btree;

import nu.marginalia.util.multimap.MultimapFileLongSlice;

import java.io.IOException;

public interface WriteCallback {
    void write(MultimapFileLongSlice slice) throws IOException;
}
