package nu.marginalia.util.array.functional;

import java.io.IOException;

public interface LongBinaryIOOperation {
    long apply(long left, long right) throws IOException;
}
