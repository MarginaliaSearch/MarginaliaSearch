package nu.marginalia.util;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/** For managing random writes on SSDs
 *
 * See https://en.wikipedia.org/wiki/Write_amplification
 * */
public class RandomWriteFunnel implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RandomWriteFunnel.class);
    private final DataBin[] bins;

    private final int binSize;

    public RandomWriteFunnel(Path tempDir, long size, int binSize) throws IOException {
        this.binSize = binSize;

        if (size > 0) {
            int binCount = (int) (size / binSize + ((size % binSize) != 0L ? 1 : 0));
            bins = new DataBin[binCount];
            for (int i = 0; i < binCount; i++) {
                bins[i] = new DataBin(tempDir, Math.min((int) (size - binSize * i), binSize));
            }
        }
        else {
            bins = new DataBin[0];
        }
    }

    @SneakyThrows
    public void put(long address, long data) {
        int bin = (int)(address / binSize);
        int offset = (int)(address%binSize);

        bins[bin].put(offset, data);
    }

    public void write(FileChannel o) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(binSize*8);

        for (var bin : bins) {
            buffer.clear();
            bin.eval(buffer);

            while (buffer.hasRemaining()) {
                o.write(buffer);
            }
        }
    }

    @Override
    public void close() throws IOException {
        for (DataBin bin : bins) {
            bin.close();
        }
    }

    static class DataBin implements AutoCloseable {
        private final ByteBuffer buffer;
        private final int size;
        private final FileChannel channel;
        private final File file;

        DataBin(Path tempDir, int size) throws IOException {
            buffer = ByteBuffer.allocateDirect(360_000);
            this.size = size;
            file = Files.createTempFile(tempDir, "scatter-writer", ".dat").toFile();
            channel = new RandomAccessFile(file, "rw").getChannel();
        }

        void put(int address, long data) throws IOException {
            if (buffer.remaining() < 12) {
                flushBuffer();
            }

            buffer.putInt(address);
            buffer.putLong(data);
        }

        private void flushBuffer() throws IOException {
            if (buffer.position() == 0)
                return;

            buffer.flip();
            while (buffer.hasRemaining())
                channel.write(buffer);

            buffer.clear();
        }

        private void eval(ByteBuffer dest) throws IOException {
            flushBuffer();
            channel.force(false);

            channel.position(0);
            buffer.clear();
            dest.clear();
            for (int i = 0; i < size; i++) {
                dest.putLong(0L);
            }
            dest.position(0);
            dest.limit(size*8);
            while (channel.position() < channel.size()) {
                int rb = channel.read(buffer);
                if (rb < 0) {
                    break;
                }
                buffer.flip();
                while (buffer.limit() - buffer.position() >= 12) {
                    int addr = 8 * buffer.getInt();
                    long data = buffer.getLong();

                    try {
                        dest.putLong(addr, data);
                    }
                    catch (IndexOutOfBoundsException ex) {
                        logger.info("!!!bad[{}]={}", addr, data);
                    }
                }
                buffer.compact();
            }
        }

        @Override
        public void close() throws IOException {
            channel.close();
            file.delete();
        }
    }
}
