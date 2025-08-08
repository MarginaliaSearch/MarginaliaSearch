package nu.marginalia.index;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.sequence.io.BitReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class PrioIndexEntrySource implements EntrySource {
    private final String name;

    private final ByteBuffer readData = ByteBuffer.allocate(8*1024);
    private final BitReader bitReader = new BitReader(readData, this::fillReadBuffer);

    private final FileChannel docsFileChannel;
    private long dataOffsetStartB;
    private final long wordId;

    private final int numItems;
    private int readItems = 0;

    int prevRank = -1;
    int prevDomainId = -1;
    int prevDocOrd = -1;

    public PrioIndexEntrySource(String name,
                                FileChannel docsFileChannel,
                                long dataOffsetStartB,
                                long wordId)
    {
        this.name = name;
        this.docsFileChannel = docsFileChannel;
        this.dataOffsetStartB = dataOffsetStartB;
        this.wordId = wordId;

        // sneaky read of the header to get item count upfront

        try {
            readData.limit(4);

            int rb = docsFileChannel.read(readData, dataOffsetStartB);
            assert rb == 4;
            readData.flip();
            numItems = readData.getInt() & 0x3FFF_FFFF;

            readData.position(0);
            readData.limit(0);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to read index data.", ex);
        }
    }

    @Override
    public void skip(int n) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @SuppressWarnings("preview")
    public void read(LongQueryBuffer buffer) {
        var outputBuffer = buffer.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        outputBuffer.clear();

        while (outputBuffer.hasRemaining() && readItems++ < numItems) {
            int rank;
            int domainId;
            int docOrd;

            int code = bitReader.get(2);
            if (code == 0b11) {
                // header
                bitReader.get(30); // skip 30 bits for the size header

                rank = bitReader.get(7);
                domainId = bitReader.get(31);
                docOrd = bitReader.get(26);
            }
            else if (code == 0b10) {
                rank = prevRank + bitReader.getGamma();
                domainId = bitReader.get(31);
                docOrd = bitReader.get(26);
            }
            else if (code == 0b01) {
                rank = prevRank;
                domainId = bitReader.getDelta() + prevDomainId;
                docOrd = bitReader.getDelta() - 1;
            }
            else if (code == 0b00) {
                rank = prevRank;
                domainId = prevDomainId;
                docOrd = prevDocOrd + bitReader.getGamma();
            }
            else {
                throw new IllegalStateException("??? found code " + code);
            }

            long encodedId = UrlIdCodec.encodeId(rank, domainId, docOrd);

            outputBuffer.putLong(
                    encodedId
            );

            prevRank = rank;
            prevDomainId = domainId;
            prevDocOrd = docOrd;
        }

        buffer.end = outputBuffer.position() / 8;

        buffer.uniq();
    }

    private void fillReadBuffer() {
        try {
            readData.compact();
            int rb = docsFileChannel.read(readData, dataOffsetStartB);
            if (rb > 0) {
                dataOffsetStartB += rb;
            }
            readData.flip();
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to read index data.", ex);
        }
    }

    @Override
    public boolean hasMore() {
        return readItems < numItems;
    }


    @Override
    public String indexName() {
        return name + ":" + Long.toHexString(wordId);
    }
}
