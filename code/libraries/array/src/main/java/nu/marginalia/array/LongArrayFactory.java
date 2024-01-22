package nu.marginalia.array;

import nu.marginalia.array.page.SegmentLongArray;
import nu.marginalia.array.page.UnsafeLongArray;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;

public class LongArrayFactory {
    public static LongArray onHeapConfined(long size) {
        return UnsafeLongArray.onHeap(Arena.ofConfined(), size);
    }

    public static LongArray onHeapShared(long size) {
        return UnsafeLongArray.onHeap(Arena.ofShared(), size);
    }

    public static LongArray mmapForReadingConfined(Path filename) throws IOException  {
        return UnsafeLongArray.fromMmapReadOnly(Arena.ofConfined(), filename,
                0,
                Files.size(filename) / 8);
    }

    public static LongArray mmapForReadingShared(Path filename) throws IOException  {
        return UnsafeLongArray.fromMmapReadOnly(Arena.ofShared(), filename,
                0,
                Files.size(filename) / 8);
    }

    public static LongArray mmapForModifyingConfined(Path filename) throws IOException  {
        return UnsafeLongArray.fromMmapReadWrite(Arena.ofConfined(), filename,
                0, Files.size(filename));
    }

    public static LongArray mmapForModifyingShared(Path filename) throws IOException  {
        return UnsafeLongArray.fromMmapReadWrite(Arena.ofShared(), filename,
                0,
                Files.size(filename) / 8);
    }

    public static LongArray mmapForWritingConfined(Path filename, long size) throws IOException  {
        return UnsafeLongArray.fromMmapReadWrite(Arena.ofConfined(), filename,
                0, size);
    }

    public static LongArray mmapForWritingShared(Path filename, long size) throws IOException  {
        return UnsafeLongArray.fromMmapReadWrite(Arena.ofShared(), filename,
                0, size);
    }
}
