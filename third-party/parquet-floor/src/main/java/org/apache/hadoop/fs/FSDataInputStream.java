package org.apache.hadoop.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class FSDataInputStream extends InputStream {
    private final RandomAccessFile input;

    public FSDataInputStream(org.apache.hadoop.fs.Path p) throws FileNotFoundException {
        this.input = new RandomAccessFile(p.file(), "r");
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        try {
            input.readFully(buf, off, len);
            return len;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public void seek(long pos) {
        try {
            input.seek(pos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readFully(byte[] buf, int a, int b) {
        try {
            input.readFully(buf, a, b);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
