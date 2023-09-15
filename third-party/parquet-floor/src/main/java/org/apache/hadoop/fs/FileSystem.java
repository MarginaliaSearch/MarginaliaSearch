package org.apache.hadoop.fs;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;

public class FileSystem {

    public FileStatus getFileStatus(org.apache.hadoop.fs.Path p) {
        return new FileStatus(p);
    }

    public org.apache.hadoop.fs.Path makeQualified(org.apache.hadoop.fs.Path p) {
        return p;
    }

    public URI getUri() {
        try {
            return new URI("http://localhost/");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public short getDefaultReplication(org.apache.hadoop.fs.Path p) {
        return 0;
    }

    public long getDefaultBlockSize(org.apache.hadoop.fs.Path p) {
        return 1024;
    }

    public FSDataInputStream open(org.apache.hadoop.fs.Path p) {
        try {
            return new FSDataInputStream(p);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public org.apache.hadoop.fs.FSDataOutputStream create(org.apache.hadoop.fs.Path p, boolean a, int b, short c, long d) {
        try {
            return new FSDataOutputStream(p);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
