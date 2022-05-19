/*
 * CountingOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.OutputStream;
import java.io.IOException;

class CountingOutputStream extends FinishableOutputStream {
    private final OutputStream out;
    private long size = 0;

    public CountingOutputStream(OutputStream out) {
        this.out = out;
    }

    public void write(int b) throws IOException {
        out.write(b);
        if (size >= 0)
            ++size;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        if (size >= 0)
            size += len;
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        out.close();
    }

    public long getSize() {
        return size;
    }
}
