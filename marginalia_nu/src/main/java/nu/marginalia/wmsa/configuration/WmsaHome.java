package nu.marginalia.wmsa.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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

    public static Path getIPLocationDatabse() {
        return getHomePath().resolve("data").resolve("IP2LOCATION-LITE-DB1.CSV");
    }

    public static Path getDisk(String name) throws IOException {
        Path p = Path.of(getDiskProperties().getProperty(name));
        if (!Files.isDirectory(p)) {
            throw new IOException(name + " does not exist!");
        }
        return p;
    }

    public static Properties getDiskProperties() throws IOException {
        Path settingsFile = getHomePath().resolve("conf/disks.properties");

        if (Files.isRegularFile(settingsFile)) {
            try (var is = Files.newInputStream(settingsFile)) {
                var props = new Properties();
                props.load(is);
                return props;
            }
        }
        else {
            throw new IOException("Could not find disk settings " + settingsFile);
        }
    }
}
