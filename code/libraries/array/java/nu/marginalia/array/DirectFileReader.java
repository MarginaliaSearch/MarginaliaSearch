package nu.marginalia.array;

import nu.marginalia.ffi.LinuxSystemCalls;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

public class DirectFileReader implements AutoCloseable {
    int fd;

    public DirectFileReader(Path filename) throws IOException {
        fd = LinuxSystemCalls.openDirect(filename);
        if (fd < 0) {
            throw new IOException("Error opening direct file: " + filename);
        }
    }

    public void readAligned(LongArray dest, long offset) throws IOException {
        readAligned(dest.getMemorySegment(), offset);
    }

    public void readAligned(MemorySegment segment, long offset) throws IOException {
        if (LinuxSystemCalls.readAt(fd, segment, offset) != segment.byteSize()) {
            throw new IOException("Failed to read data at " + offset);
        }
    }

    public void readUnaligned(MemorySegment dest, MemorySegment alignedBuffer, long fileOffset) throws IOException {
        int destOffset = 0;

        for (long totalBytesToCopy = dest.byteSize(); totalBytesToCopy > 0; ) {
            long alignedPageAddress = fileOffset & -4096L;
            long srcPageOffset = fileOffset & 4095L;
            long srcPageEnd = Math.min(srcPageOffset + totalBytesToCopy, 4096);

            // wrapper for O_DIRECT pread
            if (LinuxSystemCalls.readAt(fd, alignedBuffer, alignedPageAddress) != alignedBuffer.byteSize()) {
                throw new IOException("Failed to read data at " + alignedPageAddress + " of size " + dest.byteSize());
            }

            int bytesToCopy = (int) (srcPageEnd - srcPageOffset);

            MemorySegment.copy(alignedBuffer, srcPageOffset, dest, destOffset, bytesToCopy);

            destOffset += bytesToCopy;
            fileOffset += bytesToCopy;
            totalBytesToCopy -= bytesToCopy;
        }
    }

    public void close() {
        LinuxSystemCalls.closeFd(fd);
    }
}
