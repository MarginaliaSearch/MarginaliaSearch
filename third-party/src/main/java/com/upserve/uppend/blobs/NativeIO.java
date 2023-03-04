package com.upserve.uppend.blobs;


import com.kenai.jffi.MemoryIO;
import jnr.ffi.LibraryLoader;
import jnr.ffi.types.size_t;

import java.io.IOException;
import java.nio.MappedByteBuffer;

// https://github.com/upserve/uppend/blob/70967c6f24d7f1a3bbc18799f485d981da93f53b/src/main/java/com/upserve/uppend/blobs/NativeIO.java
// MIT License

public class NativeIO {

    private static final NativeC nativeC = LibraryLoader.create(NativeC.class).load("c");
    public static final int pageSize = nativeC.getpagesize(); // 4096 on most Linux

    public enum Advice {
        // These seem to be fairly stable https://github.com/torvalds/linux
        // TODO add to https://github.com/jnr/jnr-constants
        Normal(0), Random(1), Sequential(2), WillNeed(3), DontNeed(4);
        private final int value;
        Advice(int val) {
            this.value = val;
        }
    }

    public interface NativeC {
        int madvise(@size_t long address, @size_t long size, int advice);
        int getpagesize();
    }

    static long alignedAddress(long address) {
        return address & (- pageSize);
    }

    static long alignedSize(long address, int capacity) {
        long end = address + capacity;
        end = (end + pageSize - 1) & (-pageSize);
        return end - alignedAddress(address);
    }

    public static void madvise(MappedByteBuffer buffer, Advice advice) throws IOException {

        final long address = MemoryIO.getInstance().getDirectBufferAddress(buffer);
        final int capacity = buffer.capacity();

        long alignedAddress = alignedAddress(address);
        long alignedSize = alignedSize(alignedAddress, capacity);

        int val = nativeC.madvise(alignedAddress, alignedSize, advice.value);

        if (val != 0) {
            throw new IOException(String.format("System call madvise failed with code: %d", val));
        }
    }

    public static void madviseRange(MappedByteBuffer buffer, Advice advice, long offset, int length) throws IOException {

        final long address = MemoryIO.getInstance().getDirectBufferAddress(buffer);

        long alignedAddress = alignedAddress(address+offset);
        long alignedSize = alignedSize(alignedAddress, length);

        int val = nativeC.madvise(alignedAddress, alignedSize, advice.value);

        if (val != 0) {
            throw new IOException(String.format("System call madvise failed with code: %d", val));
        }
    }
}