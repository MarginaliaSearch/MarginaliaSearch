package nu.marginalia.array.functional;

import java.io.IOException;

public interface IntBinaryIOOperation {
    int apply(int left, int right) throws IOException;
}
