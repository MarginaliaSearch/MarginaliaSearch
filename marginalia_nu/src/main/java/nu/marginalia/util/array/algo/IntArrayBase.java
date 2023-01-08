package nu.marginalia.util.array.algo;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public interface IntArrayBase extends BulkTransferArray<IntBuffer> {
    int get(long pos);

    void set(long pos, int value);

    long size();

    default void fill(long start, long end, int val) {
        for (long v = start; v < end; v++) {
            set(v, val);
        }
    }

    default void increment(long pos) {
        set(pos, get(pos) + 1);
    }

    default void swap(long pos1, long pos2) {
        int tmp = get(pos1);
        set(pos1, get(pos2));
        set(pos2, tmp);
    }

    default void swapn(int n, long pos1, long pos2) {
        for (int i = 0; i < n; i++) {
            int tmp = get(pos1+i);
            set(pos1+i, get(pos2+i));
            set(pos2+i, tmp);
        }
    }

    default int getAndIncrement(long pos) {
        int val = get(pos);
        set(pos, val + 1);
        return val;
    }

    default void set(long start, long end, IntBuffer buffer, int bufferStart) {
        for (int i = 0; i < (end-start); i++) {
            set(start+i, buffer.get(i + bufferStart));
        }
    }
    default void get(long start, long end, IntBuffer buffer, int bufferStart) {
        for (int i = 0; i < (end-start); i++) {
            buffer.put(i + bufferStart, get(start + i));
        }
    }

    default void get(long start, IntBuffer buffer) {
        get(start, start + buffer.remaining(), buffer, buffer.position());
    }

    default void get(long start, long end, int[] buffer) {
        for (int i = 0; i < (end-start); i++) {
            buffer[i] = get(start + i);
        }
    }

    void write(Path file) throws IOException;

    void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException;
}
