package nu.marginalia.array;

import java.io.IOException;

/** Appends ranges of longs independently of the destination storage format. */
public interface LongArrayWriter {
    /** Append the source range [start, end), with offsets measured in longs. */
    void put(LongArray source, long start, long end) throws IOException;
}
