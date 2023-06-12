package nu.marginalia.bigstring;

import java.nio.ByteBuffer;

public class CompressedBigString implements BigString {
    private final int originalSize;
    private final int length;
    private final ByteBuffer encoded;

    private final static CompressionBufferPool bufferPool = new CompressionBufferPool();

    public CompressedBigString(String stringValue) {
        encoded = bufferPool.bufferForThread().compress(stringValue);
        originalSize = encoded.position();
        length = stringValue.length();
    }

    @Override
    public String decode() {
        return bufferPool.bufferForThread().decompress(encoded, length, originalSize);
    }

    @Override
    public int length() {
        return length;
    }
}
