package nu.marginalia.language.statistics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DenseBitMap {
    public static final long MAX_CAPACITY_2GB_16BN_ITEMS=(1L<<34)-8;

    public final long cardinality;
    private final ByteBuffer buffer;

    public DenseBitMap(long cardinality) {
        this.cardinality = cardinality;

        boolean misaligned = (cardinality & 7) > 0;
        this.buffer = ByteBuffer.allocateDirect((int)((cardinality / 8) + (misaligned ? 1 : 0)));
    }

    public static DenseBitMap loadFromFile(Path file) throws IOException {
        long size = Files.size(file);
        var dbm = new DenseBitMap(size/8);

        try (var bc = Files.newByteChannel(file)) {
            while (dbm.buffer.position() < dbm.buffer.capacity()) {
                bc.read(dbm.buffer);
            }
        }
        dbm.buffer.clear();

        return dbm;
    }

    public void writeToFile(Path file) throws IOException {

        try (var bc = Files.newByteChannel(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            while (buffer.position() < buffer.capacity()) {
                bc.write(buffer);
            }
        }

        buffer.clear();
    }

    public boolean get(long pos) {
        return (buffer.get((int)(pos >>> 3)) & ((byte)1 << (int)(pos & 7))) != 0;
    }

    /** Set the bit indexed by pos, returns
     *  its previous value.
     */
    public boolean set(long pos) {
        int offset = (int) (pos >>> 3);
        int oldVal = buffer.get(offset);
        int mask = (byte) 1 << (int) (pos & 7);
        buffer.put(offset, (byte) (oldVal | mask));
        return (oldVal & mask) != 0;
    }

    public void clear(long pos) {
        int offset = (int)(pos >>> 3);
        buffer.put(offset, (byte)(buffer.get(offset) & ~(byte)(1 << (int)(pos & 7))));
    }
}
