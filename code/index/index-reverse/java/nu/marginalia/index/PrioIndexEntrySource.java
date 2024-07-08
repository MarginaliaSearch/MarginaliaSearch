package nu.marginalia.index;

import lombok.SneakyThrows;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.query.EntrySource;

import java.nio.channels.FileChannel;

import static java.lang.Math.min;

public class PrioIndexEntrySource implements EntrySource {
    private final String name;

    int posL;
    int endOffsetL;

    private final FileChannel docsFileChannel;
    private final long dataOffsetStartB;
    private final long wordId;

    public PrioIndexEntrySource(String name,
                                int numEntriesL,
                                FileChannel docsFileChannel,
                                long dataOffsetStartB,
                                long wordId)
    {
        this.name = name;
        this.docsFileChannel = docsFileChannel;
        this.dataOffsetStartB = dataOffsetStartB;
        this.wordId = wordId;

        posL = 0;
        endOffsetL = posL + numEntriesL;
    }

    @Override
    public void skip(int n) {
        posL += n;
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("preview")
    public void read(LongQueryBuffer buffer) {
        buffer.reset();
        buffer.end = min(buffer.end, endOffsetL - posL);

        var byteBuffer = buffer.data.getMemorySegment().asByteBuffer();
        byteBuffer.clear();
        byteBuffer.limit(buffer.end * 8);

        while (byteBuffer.hasRemaining()) {
            int rb = docsFileChannel.read(byteBuffer, dataOffsetStartB + posL * 8L + byteBuffer.position());
            if (rb == -1) {
                throw new IllegalStateException("Unexpected end of file while reading index data.");
            }
        }

        posL += buffer.end;
        buffer.uniq();
    }


    @Override
    public boolean hasMore() {
        return posL < endOffsetL;
    }


    @Override
    public String indexName() {
        return name + ":" + Long.toHexString(wordId);
    }
}
