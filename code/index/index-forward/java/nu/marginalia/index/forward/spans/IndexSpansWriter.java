package nu.marginalia.index.forward.spans;

import nu.marginalia.sequence.VarintCodedSequence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IndexSpansWriter implements AutoCloseable {
    private final FileChannel outputChannel;
    private final ByteBuffer work = ByteBuffer.allocate(4*1024*1024).order(ByteOrder.nativeOrder());

    private long stateStartOffset = -1;
    private int stateLength = -1;

    public IndexSpansWriter(Path outputFileSpansData) throws IOException {
        this.outputChannel = (FileChannel) Files.newByteChannel(outputFileSpansData, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    public void beginRecord(int count) throws IOException {
        stateStartOffset = outputChannel.position();
        stateLength = 0;

        work.clear();
        work.putInt(count);
        work.flip();

        while (work.hasRemaining())
            stateLength += outputChannel.write(work);
    }

    public void writeSpan(byte spanCode, ByteBuffer sequenceData) throws IOException {
        work.clear();
        work.put(spanCode);
        work.put((byte) 0); // Ensure we're byte aligned
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
        ByteBuffer footer = SpansCodec.createSpanFilesFooter(SpansCodec.SpansCodecVersion.PLAIN, (int) (4096 - (outputChannel.position() & 4095)));
        outputChannel.position(outputChannel.size());
        while (footer.hasRemaining()) {
            outputChannel.write(footer, outputChannel.size());
        }
        outputChannel.close();
    }
}
