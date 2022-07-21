package nu.marginalia.util.multimap;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;

public class MultimapFileLongOffsetSlice implements MultimapFileLongSlice {
    private final long off;
    private final MultimapFileLongSlice map;

    public MultimapFileLongOffsetSlice(MultimapFileLongSlice map, long off) {
        this.off = off;
        this.map = map;
    }

    @Override
    public long size() {
        return map.size() - off;
    }

    @Override
    public void put(long idx, long val) {
        map.put(off+idx, val);
    }

    @Override
    public void setRange(long idx, int n, long val) {
        map.setRange(off+idx, n, val);
    }

    @Override
    public long get(long idx) {
        return map.get(off+idx);
    }

    @Override
    public void read(long[] vals, long idx) {
        map.read(vals, idx+off);
    }

    @Override
    public void read(long[] vals, int n, long idx) {
        map.read(vals, n, idx+off);
    }

    @Override
    public void read(LongBuffer vals, long idx) { map.read(vals, idx+off); }

    @Override
    public void write(long[] vals, long idx) {
        map.write(vals, idx+off);
    }

    @Override
    public void write(long[] vals, int n, long idx) {
        map.write(vals, n, idx+off);
    }

    @Override
    public void write(LongBuffer vals, long idx) {
        map.write(vals, idx+off);
    }

    @Override
    public void transferFromFileChannel(FileChannel sourceChannel, long destOffset, long sourceStart, long sourceEnd)
            throws IOException {
        map.transferFromFileChannel(sourceChannel, destOffset + off, sourceStart, sourceEnd);
    }

    @Override
    public MultimapFileLongSlice atOffset(long off) {
        // If we don't override this, the default implementation would build a pyramid of
        //   MultimapFileLongSlice(MultimapFileLongSlice(MultimapFileLongSlice(MultimapFileLongSlice(MultimapFileLongSlice(...)))
        // if this is called iteratively (e.g. to walk over a file)

        return new MultimapFileLongOffsetSlice(map, this.off + off);
    }
}
