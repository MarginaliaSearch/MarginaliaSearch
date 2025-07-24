package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@SuppressWarnings("preview")
public class PlainForwardIndexSpansReader implements ForwardIndexSpansReader {
    private final FileChannel spansFileChannel;

    public PlainForwardIndexSpansReader(Path spansFile) throws IOException {
        this.spansFileChannel = (FileChannel) Files.newByteChannel(spansFile, StandardOpenOption.READ);
    }

    @Override
    public DocumentSpans readSpans(Arena arena, long encodedOffset) throws IOException {
        // Decode the size and offset from the encoded offset
        long size = SpansCodec.decodeSize(encodedOffset);
        long offset = SpansCodec.decodeStartOffset(encodedOffset);

        // Allocate a buffer from the arena
        var buffer = arena.allocate(size, 4).asByteBuffer();
        buffer.clear();
        while (buffer.hasRemaining()) {
            spansFileChannel.read(buffer, offset + buffer.position());
        }
        buffer.flip();

        // Read the number of spans in the document
        int count = buffer.get();

        DocumentSpans ret = new DocumentSpans();

        // Decode each span
        while (count-- > 0) {
            byte code = buffer.get();
            buffer.get(); // Consume alignment byte
            short len = buffer.getShort();

            IntArrayList values = new IntArrayList(len);
            while (len-- > 0) {
                values.add(buffer.getInt());
            }
            ret.accept(code, values);
        }

        return ret;
    }

    @Override
    public void close() throws IOException {
        spansFileChannel.close();
    }

}
