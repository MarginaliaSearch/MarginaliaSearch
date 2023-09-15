package org.apache.hadoop.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class FSDataOutputStream extends OutputStream {
    private final RandomAccessFile output;

    public FSDataOutputStream(org.apache.hadoop.fs.Path p) throws FileNotFoundException {
        this.output = new RandomAccessFile(p.file(), "rw");
    }

    @Override
    public void write(int b) throws IOException {
        this.output.write(b);
    }

    @Override
    public void close() throws IOException {
        output.close();
    }

    public long getPos() throws IOException {
        return this.output.getFilePointer();
    }
}
