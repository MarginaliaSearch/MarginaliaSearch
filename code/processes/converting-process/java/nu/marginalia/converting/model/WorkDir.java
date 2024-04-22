package nu.marginalia.converting.model;

import java.nio.file.Path;

public record WorkDir(String dir, String logName) {
    public Path getDir() {
        return Path.of(dir);
    }

    public Path getLogFile() {
        return Path.of(dir).resolve(logName);
    }
}
