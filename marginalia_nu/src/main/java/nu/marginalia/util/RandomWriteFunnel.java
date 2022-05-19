package nu.marginalia.util;

import io.prometheus.client.Gauge;
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

    private final static Gauge write_rate = Gauge.build("wmsa_rwf_write_bytes", "Bytes/s")
            .register();
    private final static Gauge transfer_rate = Gauge.build("wmsa_rwf_transfer_bytes", "Bytes/s")
            .register();
    private static final Logger logger = LoggerFactory.getLogger(RandomWriteFunnel.class);
    private final DataBin[] bins;

    private final int binSize;

    public RandomWriteFunnel(Path tempDir, long size, int binSize) throws IOException {
        this.binSize = binSize;

        if (size > 0) {
            int binCount = (int) (size / binSize + ((size % binSize) != 0L ? 1 : 0));
            bins = new DataBin[binCount];
            for (int i = 0; i < binCount; i++) {
                bins[i] = new DataBin(tempDir, (int) Math.min(size - binSize * i, binSize));
            }
        }
        else {
            bins = new DataBin[0];
        }
    }

    public void put(long address, long data) throws IOException {
        bins[((int)(address / binSize))].put((int)(address%binSize), data);
    }

    public void write(FileChannel o) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(binSize*8);
        logger.debug("Writing from RWF");

        for (int i = 0; i < bins.length; i++) {
            var bin = bins[i];
            buffer.clear();
            bin.eval(buffer);

            while (buffer.hasRemaining()) {
                int wb = o.write(buffer);
                write_rate.set(wb);
            }
        }
        logger.debug("Done");
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
            buffer.putInt(address);
            buffer.putLong(data);

            if (buffer.capacity() - buffer.position() < 12) {
                flushBuffer();
            }
        }

        private void flushBuffer() throws IOException {
            if (buffer.position() == 0)
                return;

            buffer.flip();
            while (channel.write(buffer) > 0);
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
                else {
                    transfer_rate.set(rb);
                }
                buffer.flip();
                while (buffer.limit() - buffer.position() >= 12) {
                    int addr = buffer.getInt();
                    long data = buffer.getLong();
                    dest.putLong(8*addr, data);
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
