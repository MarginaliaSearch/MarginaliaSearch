package org.apache.hadoop.io.compress;

public final class CodecPool {

    private CodecPool() { /* prevent instantiation */ }
    public static Decompressor getDecompressor(CompressionCodec codec) {
        return codec.createDecompressor();
    }

    public static void returnDecompressor(Decompressor decompressor) {

    }

    public static Compressor getCompressor(CompressionCodec codec) {
        return codec.createCompressor();
    }

    public static void returnCompressor(Compressor compressor) {

    }
}
