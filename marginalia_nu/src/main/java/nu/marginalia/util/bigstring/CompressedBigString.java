package nu.marginalia.util.bigstring;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.nio.charset.StandardCharsets;

public class CompressedBigString implements BigString {
    private final int originalSize;
    private final int length;
    private final byte[] encoded;

    private static final LZ4Factory lz4Factory = LZ4Factory.fastestInstance();;
    private static final LZ4Compressor compressor = lz4Factory.fastCompressor();
    private static final LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();

    public CompressedBigString(String stringValue) {
        byte[] byteValue = stringValue.getBytes(StandardCharsets.UTF_16);
        originalSize = byteValue.length;
        encoded = compressor.compress(byteValue);
        length = stringValue.length();
    }

    @Override
    public String decode() {
        return new String(getBytes(), StandardCharsets.UTF_16);
    }

    @Override
    public byte[] getBytes() {
        return decompressor.decompress(encoded, originalSize);
    }

    @Override
    public int length() {
        return length;
    }
}
