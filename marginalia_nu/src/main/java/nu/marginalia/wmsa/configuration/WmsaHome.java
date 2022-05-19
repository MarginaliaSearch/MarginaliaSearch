package nu.marginalia.wmsa.configuration;

import java.nio.file.Files;
import java.nio.file.Path;

public class WmsaHome {
    private static final String DEFAULT = "/var/lib/wmsa";

    public static Path get() {
        var ret = Path.of(System.getProperty("WMSA_HOME", DEFAULT));
        if (!Files.isDirectory(ret)) {
            throw new IllegalStateException("Could not find WMSA_HOME, either set environment variable or ensure " + DEFAULT + " exists");
        }
        return ret;
    }
}
