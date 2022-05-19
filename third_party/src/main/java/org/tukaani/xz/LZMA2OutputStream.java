/*
 * LZMA2OutputStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.IOException;

//
// TODO: This creates a valid LZMA2 stream but it doesn't compress.
//       So this is useless except for testing the .xz container support.
//

class LZMA2OutputStream extends FinishableOutputStream {
    private final FinishableOutputStream out;

    static int getMemoryUsage(LZMA2Options options) {
        // TODO
        return 1;
    }

    LZMA2OutputStream(FinishableOutputStream out, LZMA2Options options) {
        this.out = out;
    }

    public void write(int b) throws IOException {
        byte[] buf = new byte[1];
        buf[0] = (byte)b;
        write(buf, 0, 1);
    }

    public void write(byte[] buf, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length)
            throw new IllegalArgumentException();

        while (off > 0x10000) {
            writeChunk(buf, off, 0x10000);
            off += 0x10000;
            len -= 0x10000;
        }

        writeChunk(buf, off, len);
    }

    private void writeChunk(byte[] buf, int off, int len) throws IOException {
        out.write(0x01);
        out.write((len - 1) >>> 8);
        out.write(len - 1);
        out.write(buf, off, len);
    }

    private void writeEndMarker() throws IOException {
        // TODO: Flush incomplete chunk.
        out.write(0x00);
    }

    public void flush() throws IOException {
        throw new UnsupportedOptionsException(
                "Flushing LZMA2OutputStream not implemented yet");
    }

    public void finish() throws IOException {
        writeEndMarker();
        out.finish();
    }

    public void close() throws IOException {
        writeEndMarker();
        out.close();
    }
}
