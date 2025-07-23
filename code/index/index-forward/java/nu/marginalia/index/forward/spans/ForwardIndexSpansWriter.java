package nu.marginalia.index.forward.spans;

import nu.marginalia.sequence.VarintCodedSequence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ForwardIndexSpansWriter implements AutoCloseable {
    private final FileChannel outputChannel;
    private final ByteBuffer work = ByteBuffer.allocate(65536);

    private long stateStartOffset = -1;
    private int stateLength = -1;

    public ForwardIndexSpansWriter(Path outputFileSpansData) throws IOException {
        this.outputChannel = (FileChannel) Files.newByteChannel(outputFileSpansData, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    public void beginRecord(int count) throws IOException {
        stateStartOffset = outputChannel.position();
        stateLength = 0;

        work.clear();
        work.put((byte) count);
        work.flip();

        while (work.hasRemaining())
            stateLength += outputChannel.write(work);
    }

    public void writeSpan(byte spanCode, ByteBuffer sequenceData) throws IOException {
        work.clear();
        work.put(spanCode);
        var sequence = new VarintCodedSequence(sequenceData);
        work.putShort((short) sequence.valueCount());

        var iter = sequence.iterator();
        while (iter.hasNext()) {
            work.putInt(iter.nextInt());
        }
        work.flip();

        stateLength += outputChannel.write(work);
    }

    public long endRecord() {
        return SpansCodec.encode(stateStartOffset, stateLength);
    }

    @Override
    public void close() throws IOException {
        outputChannel.close();
    }
}
