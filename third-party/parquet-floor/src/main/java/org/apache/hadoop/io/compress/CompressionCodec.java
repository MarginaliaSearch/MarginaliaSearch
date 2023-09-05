package org.apache.hadoop.io.compress;

import java.io.InputStream;
import java.io.OutputStream;

public interface CompressionCodec {
    Decompressor createDecompressor();
    Compressor createCompressor();
    CompressionInputStream createInputStream(InputStream is, Decompressor d);
    CompressionOutputStream createOutputStream(OutputStream os, Compressor c);
}
