package nu.marginalia.array.functional;

import java.io.IOException;

public interface LongBinaryIOOperation {
    long apply(long left, long right) throws IOException;
}
