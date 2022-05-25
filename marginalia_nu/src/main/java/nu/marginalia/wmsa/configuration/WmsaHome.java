package nu.marginalia.wmsa.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WmsaHome {
    private static final String DEFAULT = "/var/lib/wmsa";

    public static Path getHomePath() {
        var ret = Path.of(System.getProperty("WMSA_HOME", DEFAULT));
        if (!Files.isDirectory(ret)) {
            throw new IllegalStateException("Could not find WMSA_HOME, either set environment variable or ensure " + DEFAULT + " exists");
        }
        return ret;
    }

    public static HostsFile getHostsFile() {
        Path hostsFile = getHomePath().resolve("conf/hosts");
        if (Files.isRegularFile(hostsFile)) {
            try {
                return new HostsFile(hostsFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load hosts file " + hostsFile, e);
            }
        }
        else {
            return new HostsFile();
        }
    }
}
