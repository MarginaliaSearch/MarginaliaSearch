package nu.marginalia.index.forward.spans;

import nu.marginalia.sequence.GammaCodedSequence;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@SuppressWarnings("preview")
public class ForwardIndexSpansReader implements AutoCloseable {
    private final FileChannel spansFileChannel;

    public ForwardIndexSpansReader(Path spansFile) throws IOException {
        this.spansFileChannel = (FileChannel) Files.newByteChannel(spansFile, StandardOpenOption.READ);
    }

    public DocumentSpans readSpans(Arena arena, long encodedOffset) throws IOException {
        long size = SpansCodec.decodeSize(encodedOffset);
        long offset = SpansCodec.decodeStartOffset(encodedOffset);

        var buffer = arena.allocate(size).asByteBuffer();
        buffer.clear();
        while (buffer.hasRemaining()) {
            spansFileChannel.read(buffer, offset + buffer.position());
        }
        buffer.flip();

        int count = buffer.get();

        DocumentSpans ret = new DocumentSpans();

        while (count-- > 0) {
            byte code = buffer.get();
            short len = buffer.getShort();

            ret.accept(code, new GammaCodedSequence(buffer.slice(buffer.position(), len)));

            // Reset the buffer position to the end of the span
            buffer.position(buffer.position() + len);
        }

        return ret;
    }

    @Override
    public void close() throws IOException {
        spansFileChannel.close();
    }

}
