package nu.marginalia.index.forward.spans;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ForwardIndexSpansWriter implements AutoCloseable {
    private final FileChannel outputChannel;
    private final ByteBuffer work = ByteBuffer.allocate(32);

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
        work.putShort((short) sequenceData.remaining());
        work.flip();

        while (work.hasRemaining() || sequenceData.hasRemaining()) {
            stateLength += (int) outputChannel.write(new ByteBuffer[]{work, sequenceData});
        }
    }

    public long endRecord() {
        return SpansCodec.encode(stateStartOffset, stateLength);
    }

    @Override
    public void close() throws IOException {
        outputChannel.close();
    }
}
