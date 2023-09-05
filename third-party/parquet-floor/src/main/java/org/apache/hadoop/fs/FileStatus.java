package org.apache.hadoop.fs;

public class FileStatus {
    private final org.apache.hadoop.fs.Path path;

    public FileStatus(org.apache.hadoop.fs.Path p) {
        path = p;
    }

    public boolean isFile() {
        return true;
    }

    public org.apache.hadoop.fs.Path getPath() {
        return path;
    }

    public long getLen() {
        return path.file().length();
    }
}
