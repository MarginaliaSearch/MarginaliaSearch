package nu.marginalia.util.multimap;

import com.upserve.uppend.blobs.NativeIO;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static nu.marginalia.util.FileSizeUtil.readableSize;


public class MultimapFileLong implements AutoCloseable, MultimapFileLongSlice {

    private final ArrayList<LongBuffer> buffers = new ArrayList<>();
    private final ArrayList<MappedByteBuffer> mappedByteBuffers = new ArrayList<>();
    private final FileChannel.MapMode mode;
    private final int bufferSize;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final FileChannel channel;

    private final long mapSize;
    private final long fileLength;
    private long mappedSize;
    final static long WORD_SIZE = 8;

    private NativeIO.Advice defaultAdvice = null;

    public static MultimapFileLong forReading(Path file) throws IOException {
        long fileSize = Files.size(file);
        int bufferSize = getBufferSize(fileSize, false);

        return new MultimapFileLong(file.toFile(), READ_ONLY, Files.size(file), bufferSize);
    }

    public static MultimapFileLong forOutput(Path file, long estimatedSize) throws IOException {
        return new MultimapFileLong(file.toFile(), READ_WRITE, 0, getBufferSize(estimatedSize, true));
    }

    private static int getBufferSize(long totalSize, boolean write) {
        int defaultBig = 2<<23;
        if (totalSize > Integer.MAX_VALUE/WORD_SIZE) {
            return defaultBig;
        }
        else if (write && totalSize < 8*1024*1024) {
            return 8*1024*1024;
        }
        else {
            return (int) Math.min(totalSize, defaultBig);
        }
    }


    public MultimapFileLong(File file,
                            FileChannel.MapMode mode,
                            long mapSize,
                            int bufferSize) throws IOException {

        this(new RandomAccessFile(file, translateToRAFMode(mode)), mode, mapSize, bufferSize);
    }

    private static String translateToRAFMode(FileChannel.MapMode mode) {
        if (READ_ONLY.equals(mode)) {
            return "r";
        } else if (READ_WRITE.equals(mode)) {
            return "rw";
        }
        return "rw";
    }


    public MultimapFileLong(RandomAccessFile file,
                            FileChannel.MapMode mode,
                            long mapSizeBytes,
                            int bufferSizeWords) throws IOException {
        this.mode = mode;
        this.bufferSize = bufferSizeWords;
        this.mapSize = mapSizeBytes;
        this.fileLength = file.length();

        channel = file.getChannel();
        mappedSize = 0;

        logger.debug("Creating multimap file size = {} / buffer size = {}, mode = {}",
                readableSize(mapSizeBytes), readableSize(8L*bufferSizeWords), mode);
    }

    public MultimapSearcherBase createSearcher() {
        return new MultimapSearcherBase(this);
    }
    public MultimapSorter createSorter(Path tmpFile, int internalSortLimit) {
        return new MultimapSorter(this, tmpFile, internalSortLimit);
    }

    @SneakyThrows
    public void advice(NativeIO.Advice advice) {
        this.defaultAdvice = advice;
        for (var buffer : mappedByteBuffers) {
                NativeIO.madvise(buffer, advice);
        }
    }

    @SneakyThrows
    public void advice0(NativeIO.Advice advice) {
        NativeIO.madvise(mappedByteBuffers.get(0), advice);
    }

    @SneakyThrows
    public void adviceRange(NativeIO.Advice advice, long startLongs, long lengthLongs) {
        long endLongs = (startLongs+lengthLongs);

        if (endLongs >= mappedSize)
            grow(endLongs);


        int startIdx = (int)(startLongs / bufferSize);
        int endIdx = (int)(endLongs / bufferSize);

        if (startIdx != endIdx) {
            long offsetStart = (startLongs % bufferSize) * WORD_SIZE;
            NativeIO.madviseRange(mappedByteBuffers.get(startIdx), advice, offsetStart, (int) (bufferSize * WORD_SIZE - offsetStart));
            for (int i = startIdx+1; i < endIdx; i++) {
                NativeIO.madviseRange(mappedByteBuffers.get(i), advice, 0, (int)(bufferSize * WORD_SIZE));
            }
            NativeIO.madviseRange(mappedByteBuffers.get(endIdx), advice, 0, (int)((endIdx % bufferSize) * WORD_SIZE));
        }
        else {
            var buff = mappedByteBuffers.get(startIdx);
            NativeIO.madviseRange(buff, advice, (startLongs % bufferSize) * WORD_SIZE, (int) (lengthLongs * WORD_SIZE));
        }
    }

    public void pokeRange(long offset, long length) {
        for (long i = 0; i < length; i += 4096/8) {
            get(offset + i);
        }
    }

    public void force() {
        logger.debug("Forcing");

        for (MappedByteBuffer buffer: mappedByteBuffers) {
            buffer.force();
        }
    }

    @SneakyThrows
    public void grow(long posIdxRequired) {
        if (posIdxRequired*WORD_SIZE > mapSize && mode == READ_ONLY) {
            throw new IndexOutOfBoundsException(posIdxRequired + " (max " + mapSize + ")");
        }
        logger.trace("Growing to encompass {}i/{}b", posIdxRequired, posIdxRequired*WORD_SIZE);
        long start;
        if (buffers.isEmpty()) {
            start = 0;
        }
        else {
            start = (long) buffers.size() * bufferSize;
        }
        for (long posIdx = start; posIdxRequired >= posIdx; posIdx += bufferSize) {
            long posBytes = posIdx * WORD_SIZE;
            long bzBytes;
            if (mode == READ_ONLY) {
                bzBytes = Math.min(WORD_SIZE*bufferSize, mapSize - posBytes);
            }
            else {
                bzBytes = WORD_SIZE*bufferSize;
            }
            logger.trace("Allocating {}-{}", posBytes, posBytes+bzBytes);

            var buffer = channel.map(mode, posBytes, bzBytes);

            if (defaultAdvice != null) {
                NativeIO.madvise(buffer, defaultAdvice);
            }

            buffers.add(buffer.asLongBuffer());
            mappedByteBuffers.add(buffer);

            mappedSize += bzBytes/WORD_SIZE;
        }
    }

    @Override
    public long size() {
        return fileLength;
    }

    @Override
    public void put(long idx, long val) {
        if (idx >= mappedSize)
            grow(idx);

        try {
            buffers.get((int)(idx / bufferSize)).put((int) (idx % bufferSize), val);
        }
        catch (IndexOutOfBoundsException ex) {
            logger.error("Index out of bounds {} -> {}:{} cap {}", idx, buffers.get((int)(idx / bufferSize)), idx % bufferSize,
                    buffers.get((int)(idx / bufferSize)).capacity());
            throw new RuntimeException(ex);
        }
    }

    @Override
    public long get(long idx) {
        if (idx >= mappedSize)
            grow(idx);

        try {
            return buffers.get((int)(idx / bufferSize)).get((int)(idx % bufferSize));
        }
        catch (IndexOutOfBoundsException ex) {
            logger.error("Index out of bounds {} -> {}:{} cap {}", idx, buffers.get((int)(idx / bufferSize)), idx % bufferSize,
                    buffers.get((int)(idx / bufferSize)).capacity());
            throw new RuntimeException(ex);
        }
    }


    @Override
    public void read(long[] vals, long idx) {
        read(vals, vals.length, idx);
    }

    @Override
    public void read(long[] vals, int n, long idx) {
        if (idx+n >= mappedSize) {
            grow(idx+n);
        }

        int iN = (int)((idx + n) / bufferSize);

        for (int i = 0; i < n; ) {
            int i0 = (int)((idx + i) / bufferSize);
            int bufferOffset = (int) ((idx+i) % bufferSize);
            var buffer = buffers.get(i0);

            final int l;

            if (i0 < iN) l = bufferSize - bufferOffset;
            else l = Math.min(n - i, bufferSize - bufferOffset);

            buffer.get(bufferOffset, vals, i, l);
            i+=l;

        }

    }

    @Override
    public void read(LongBuffer vals, long idx) {
        int n = vals.limit() - vals.position();
        if (idx+n >= mappedSize) {
            grow(idx+n);
        }
        int iN = (int)((idx + n) / bufferSize);

        for (int i = 0; i < n; ) {
            int i0 = (int)((idx + i) / bufferSize);

            int bufferOffset = (int) ((idx+i) % bufferSize);
            var buffer = buffers.get(i0);

            final int l;

            if (i0 < iN) l = bufferSize - bufferOffset;
            else l = Math.min(n - i, bufferSize - bufferOffset);

            vals.put(vals.position() + i, buffer, bufferOffset, l);
            i+=l;
        }

    }


    @Override
    public void write(long[] vals, long idx) {
        write(vals, vals.length, idx);
    }

    @Override
    public void write(long[] vals, int n, long idx) {
        if (idx+n >= mappedSize) {
            grow(idx+n);
        }

        int iN = (int)((idx + n) / bufferSize);

        for (int i = 0; i < n; ) {
            int i0 = (int)((idx + i) / bufferSize);
            int bufferOffset = (int) ((idx+i) % bufferSize);
            var buffer = buffers.get(i0);

            final int l;

            if (i0 < iN) l = bufferSize - bufferOffset;
            else l = Math.min(n - i, bufferSize - bufferOffset);

            buffer.put(bufferOffset, vals, i, l);
            i+=l;

        }

    }

    @Override
    public void write(LongBuffer vals, long idx) {
        int n = vals.limit() - vals.position();
        if (idx+n >= mappedSize) {
            grow(idx+n);
        }
        int iN = (int)((idx + n) / bufferSize);

        for (int i = 0; i < n; ) {
            int i0 = (int)((idx + i) / bufferSize);

            int bufferOffset = (int) ((idx+i) % bufferSize);
            var buffer = buffers.get(i0);

            final int l;

            if (i0 < iN) l = bufferSize - bufferOffset;
            else l = Math.min(n - i, bufferSize - bufferOffset);

            buffer.put(bufferOffset, vals, vals.position() + i, l);
            i+=l;
        }

    }

    @Override
    public void setRange(long idx, int n, long val) {
        if (n == 0) return;

        if (idx+n >= mappedSize) {
            grow(idx+n);
        }
        int iN = (int)((idx + n) / bufferSize);

        for (int i = 0; i < n; ) {
            int i0 = (int)((idx + i) / bufferSize);

            int bufferOffset = (int) ((idx+i) % bufferSize);
            var buffer = buffers.get(i0);

            final int l;

            if (i0 < iN) l = bufferSize - bufferOffset;
            else l = Math.min(n - i, bufferSize - bufferOffset);

            for (int p = 0; p < l; p++) {
                buffer.put(bufferOffset + p, val);
            }

            i+=l;
        }
    }


    @Override
    public void transferFromFileChannel(FileChannel sourceChannel, long destOffset, long sourceStart, long sourceEnd) throws IOException {

        int length = (int)(sourceEnd - sourceStart);

        if (destOffset+length >= mappedSize) {
            grow(destOffset+length);
        }

        int i0 = (int)((destOffset) / bufferSize);
        int iN = (int)((destOffset + length) / bufferSize);

        int numBuffers = iN - i0 + 1;
        ByteBuffer[] buffers = new ByteBuffer[numBuffers];
        for (int i = 0; i < numBuffers; i++) {
            buffers[i] = mappedByteBuffers.get(i0 + i);
            buffers[i].clear();
        }
        if (i0 != iN) {
            int startBuf0 = (int) ((destOffset) % bufferSize) * 8;
            int endBuf0 = buffers[0].capacity() - (int) ((destOffset) % bufferSize) * 8;
            int endBufN = (int)((destOffset + length) % bufferSize)*8;
            buffers[0] = buffers[0].slice(startBuf0, endBuf0);
            buffers[numBuffers-1] = buffers[numBuffers-1].slice(0, endBufN);
        }
        else {
            buffers[0] = buffers[0].slice((int) ((destOffset) % bufferSize) * 8, 8*length);
        }

        sourceChannel.position(sourceStart*8);

        long twb = 0;
        while (twb < length * 8L) {
            long rb = sourceChannel.read(buffers, 0, buffers.length);
            if (rb < 0)
                throw new IOException();
            twb += rb;
        }

    }


    @Override
    public void close() throws IOException {
        force();

        mappedByteBuffers.clear();
        buffers.clear();

        channel.close();

        // I want to believe
        System.runFinalization();
        System.gc();
    }


}
