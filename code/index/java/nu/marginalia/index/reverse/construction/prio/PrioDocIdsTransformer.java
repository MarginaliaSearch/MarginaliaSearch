package nu.marginalia.index.reverse.construction.prio;

import nu.marginalia.array.algo.LongArrayTransformations;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.sequence.io.BitWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/** Constructs document ids list priority reverse index */
public class PrioDocIdsTransformer implements LongArrayTransformations.LongIOTransformer, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PrioDocIdsTransformer.class);

    private final FileChannel writeChannel;
    private final FileChannel readChannel;

    private final ByteBuffer readBuffer = ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(65536);

    long startL = 0;
    long writeOffsetB = 0;

    public PrioDocIdsTransformer(FileChannel writeChannel,
                                 FileChannel readChannel) {
        this.writeChannel = writeChannel;
        this.readChannel = readChannel;
    }

    @Override
    public long transform(long pos, long endL) throws IOException {

        final int sizeL = (int) ((endL - startL));
        final long startOffsetB = writeOffsetB + writeBuffer.position();

        if (sizeL == 0)
            throw new IllegalStateException("Empty range");

        int toBeRead = 8 * (sizeL);

        var bitWriter = new BitWriter(writeBuffer);

        int prevRank = -1;
        int prevDomainId = -1;
        int prevDocOrd = -1;
        boolean wroteHeader = false;

        do {
            readChannel.position(startL * 8);
            readBuffer.clear();

            readBuffer.limit(Math.min(readBuffer.capacity(), toBeRead));
            readChannel.read(readBuffer);
            readBuffer.flip();

            int distinctEntries = findDistinctEntries(readBuffer);

            if (writeBuffer.remaining() < 32) {
                writeBuffer.flip();
                int written = writeChannel.write(writeBuffer, writeOffsetB);
                writeOffsetB += written;
                writeBuffer.clear();
            }

            if (!wroteHeader) {
                // write 11b header
                bitWriter.putBits(3, 2);
                // encode number of items
                bitWriter.putBits(distinctEntries, 30);


                long firstItem = readBuffer.getLong();

                prevRank = UrlIdCodec.getRank(firstItem);
                prevDomainId = UrlIdCodec.getDomainId(firstItem);
                prevDocOrd = UrlIdCodec.getDocumentOrdinal(firstItem);

                bitWriter.putBits(prevRank, 7);
                bitWriter.putBits(prevDomainId, 31);
                bitWriter.putBits(prevDocOrd, 26);

                wroteHeader = true;
            }

            while (readBuffer.hasRemaining()) {
                if (writeBuffer.remaining() < 32) {
                    writeBuffer.flip();
                    int written = writeChannel.write(writeBuffer, writeOffsetB);
                    writeOffsetB += written;
                    writeBuffer.clear();
                }

                long nextId = readBuffer.getLong();

                // break down id components
                int rank = UrlIdCodec.getRank(nextId);
                int domainId = UrlIdCodec.getDomainId(nextId);
                int docOrd = UrlIdCodec.getDocumentOrdinal(nextId);

                // encode components
                if (rank != prevRank) {
                    bitWriter.putBits(0b10, 2);
                    bitWriter.putGamma(rank - prevRank);
                    bitWriter.putBits(domainId, 31);
                    bitWriter.putBits(docOrd, 26);
                }
                else if (domainId != prevDomainId) {
                    bitWriter.putBits(0b01, 2);
                    bitWriter.putDelta(domainId - prevDomainId);
                    bitWriter.putDelta(1 + docOrd);
                }
                else if (docOrd != prevDocOrd) {
                    bitWriter.putBits(0b00, 2);
                    bitWriter.putGamma(docOrd - prevDocOrd);
                }
                else {
                    // logger.warn("Unexpected duplicate document id: {}", nextId);
                }

                prevDocOrd = docOrd;
                prevDomainId = domainId;
                prevRank = rank;

            }

            toBeRead -= readBuffer.limit();
            readBuffer.clear();
        } while (toBeRead > 0);

        // write lingering data

        // ensure any half-written data is flushed to the buffer
        bitWriter.finishLastByte();

        // update the start input pointer
        startL = endL;
        return startOffsetB;
    }

    private int findDistinctEntries(ByteBuffer readBuffer) {
        if (readBuffer.limit() == 0)
            return 0;

        readBuffer.mark();

        int count = 1;
        long prev = readBuffer.getLong();
        long current;

        while (readBuffer.hasRemaining()) {
            if ((current = readBuffer.getLong()) != prev)
                count++;
            prev = current;
        }

        readBuffer.rewind();

        return count;
    }

    @Override
    public void close() throws IOException {
        writeBuffer.flip();
        while (writeBuffer.hasRemaining()) {
            int written = writeChannel.write(writeBuffer, writeOffsetB);
            writeOffsetB += written;
        }
        writeBuffer.clear();
    }
}
