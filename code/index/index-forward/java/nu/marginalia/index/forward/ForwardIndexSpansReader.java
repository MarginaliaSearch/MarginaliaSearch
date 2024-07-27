package nu.marginalia.index.forward;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.GammaCodedSequence;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("preview")
public class ForwardIndexSpansReader implements AutoCloseable {
    private final FileChannel spansFileChannel;

    public ForwardIndexSpansReader(Path spansFile) throws IOException {
        this.spansFileChannel = (FileChannel) Files.newByteChannel(spansFile, StandardOpenOption.READ);
    }

    public List<SpanData> readSpans(Arena arena, long encodedOffset) throws IOException {
        long size = encodedOffset & 0xFFF_FFFF;
        long offset = encodedOffset >>> 28;

        var buffer = arena.allocate(size).asByteBuffer();
        buffer.clear();
        while (buffer.hasRemaining()) {
            spansFileChannel.read(buffer, offset + buffer.position());
        }
        buffer.flip();

        int count = buffer.get();

        List<SpanData> ret = new ArrayList<>();
        while (count-- > 0) {
            byte code = buffer.get();
            short len = buffer.getShort();

            final int pos = buffer.position();

            // Decode the gamma-coded sequence; this will advance the buffer position
            // in a not entirely predictable way, so we need to save the position
            buffer.limit(buffer.position() + len);
            var sequence = new GammaCodedSequence(buffer).values();
            ret.add(new SpanData(code, sequence));

            // Reset the buffer position to the end of the span
            buffer.position(pos + len);
            buffer.limit(buffer.capacity());
        }

        return ret;
    }

    @Override
    public void close() throws IOException {
        spansFileChannel.close();
    }

    public record SpanData(byte code, IntList data) {}
}
