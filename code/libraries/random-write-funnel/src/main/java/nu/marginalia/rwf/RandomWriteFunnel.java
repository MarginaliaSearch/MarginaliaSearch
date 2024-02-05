package nu.marginalia.rwf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

/** For managing random writes on SSDs.
 * Because SSDs do not deal well with random small writes,
 *  see https://en.wikipedia.org/wiki/Write_amplification,
 * it is beneficial to pigeonhole the writes first
 * within the same general region
 * */
public class RandomWriteFunnel implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RandomWriteFunnel.class);
    private final ArrayList<DataBin> bins;
    private final Path tempDir;
    private final int binSize;

    RandomWriteFunnel(Path tempDir, int binSize) throws IOException {
        this.binSize = binSize;
        this.tempDir = tempDir;

        bins = new ArrayList<>();
    }

    public void put(long address, long data) throws IOException {
        int bin = (int)(address / binSize);
        int offset = (int)(address%binSize);

        if (bin >= bins.size()) {
            grow(bin);
        }

        bins.get(bin).put(offset, data);
    }

    private void grow(int bin) throws IOException {
        while (bins.size() <= bin) {
            bins.add(new DataBin(tempDir, binSize));
        }
    }

    public void write(ByteChannel o) throws IOException {
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

    static class DataBin {
        private final ByteBuffer buffer;
        private final int size;
        private final FileChannel channel;
        private final File file;

        DataBin(Path tempDir, int size) throws IOException {
            buffer = ByteBuffer.allocateDirect(360_000);
            this.size = size;
            file = Files.createTempFile(tempDir, "scatter-writer", ".dat").toFile();
            channel = (FileChannel) Files.newByteChannel(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ);
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
                        logger.info("Bad poke[{}]={}, this happens if an RWF is allocated with insufficient size", addr, data);
                    }
                }
                buffer.compact();
            }
        }

        public void close() throws IOException {
            channel.close();
            file.delete();
        }
    }
}
