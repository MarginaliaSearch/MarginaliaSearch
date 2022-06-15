package nu.marginalia.util.btree;

import java.io.IOException;

public interface WriteCallback {
    void write(long offset) throws IOException;
}
