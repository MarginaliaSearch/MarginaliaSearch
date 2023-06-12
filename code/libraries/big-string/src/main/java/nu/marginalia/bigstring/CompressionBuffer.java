package nu.marginalia.bigstring;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.nio.ByteBuffer;

/** Buffers for compression and decompression of strings.
 * Operations are synchronized on the buffers.
 * <p>
 * @see CompressionBufferPool CompressionBufferPool */
public class CompressionBuffer {
    private static final int BUFFER_SIZE = 8_000_000;
    private final ByteBuffer buffer;

    private static final LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
    private static final LZ4Compressor compressor = lz4Factory.fastCompressor();
    private static final LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();


    public CompressionBuffer() {
        this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    }

    /**
     * @param stringValue the string to compress
     * @return a compressed version of the string in a newly allocated ByteBuffer
     */
    public synchronized ByteBuffer compress(String stringValue) {
        final int splitPoint = stringValue.length() * 2;

        buffer.clear();

        var rawBuffer = buffer.slice(0, splitPoint);
        var compressedBuffer = buffer.slice(splitPoint, BUFFER_SIZE - splitPoint);

        rawBuffer.clear();
        rawBuffer.asCharBuffer().append(stringValue);

        // can't flip here because position and limit is in the CharBuffer representation
        rawBuffer.position(0);
        rawBuffer.limit(stringValue.length() * 2);

        compressedBuffer.clear();
        compressor.compress(rawBuffer, compressedBuffer);
        compressedBuffer.flip();

        ByteBuffer retBuffer = ByteBuffer.allocate(compressedBuffer.limit());
        retBuffer.put(compressedBuffer);
        return retBuffer;
    }

    public synchronized String decompress(ByteBuffer encoded, int length, int originalSize) {
        buffer.position(0);
        buffer.limit(length * 2);

        encoded.position(0);
        encoded.limit(originalSize);

        decompressor.decompress(encoded, buffer);

        buffer.flip();

        return buffer.asCharBuffer().toString();
    }
}
