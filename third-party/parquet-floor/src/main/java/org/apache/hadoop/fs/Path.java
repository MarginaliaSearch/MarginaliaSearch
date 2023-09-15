package org.apache.hadoop.fs;

import org.apache.hadoop.conf.Configuration;

import java.io.File;

public class Path {

    private final File file;

    public Path(String path) {
        file = new File(path);
    }

    public FileSystem getFileSystem(Configuration conf) {
        return new FileSystem();
    }

    public File file() {
        return file;
    }
}
