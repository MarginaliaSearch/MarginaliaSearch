package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
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

        var ms = arena.allocate(size, 4);
        // Allocate a buffer from the arena
        var buffer = ms.asByteBuffer();
        buffer.clear();
        while (buffer.hasRemaining()) {
            spansFileChannel.read(buffer, offset + buffer.position());
        }
        buffer.flip();

        // Read the number of spans in the document
        int count = ms.get(ValueLayout.JAVA_INT, 0);
        int pos = 4;
        DocumentSpans ret = new DocumentSpans();

        // Decode each span
        while (count-- > 0) {
            byte code = ms.get(ValueLayout.JAVA_BYTE, pos);
            short len = ms.get(ValueLayout.JAVA_SHORT, pos+2);

            IntArrayList values = new IntArrayList(len);

            pos += 4;
            for (int i = 0; i < len; i++) {
                values.add(ms.get(ValueLayout.JAVA_INT, pos + 4*i));
            }
            ret.accept(code, values);
            pos += 4*len;
        }

        return ret;
    }

    @Override
    public void close() throws IOException {
        spansFileChannel.close();
    }

}
