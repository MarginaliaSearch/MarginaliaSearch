package nu.marginalia.wmsa;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class WmsaHome {
    private static final String DEFAULT = "/var/lib/wmsa";

    public static Path getHomePath() {
        var retStr = Optional.ofNullable(System.getenv("WMSA_HOME")).orElse(DEFAULT);

        var ret = Path.of(retStr);
        if (!Files.isDirectory(ret)) {
            throw new IllegalStateException("Could not find WMSA_HOME, either set environment variable or ensure " + DEFAULT + " exists");
        }
        return ret;
    }

    public static Path getDisk(String name) {
        var pathStr = getDiskProperties().getProperty(name);
        if (null == pathStr) {
            throw new RuntimeException("Disk " + name + " was not configured");
        }
        Path p = Path.of(pathStr);
        if (!Files.isDirectory(p)) {
            throw new RuntimeException("Disk " + name + " does not exist or is not a directory!");
        }
        return p;
    }

    public static Properties getDiskProperties() {
        Path settingsFile = getHomePath().resolve("conf/disks.properties");

        if (!Files.isRegularFile(settingsFile)) {
            throw new RuntimeException("Could not find disk settings " + settingsFile);
        }

        try (var is = Files.newInputStream(settingsFile)) {
            var props = new Properties();
            props.load(is);
            return props;
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final boolean debugMode = Boolean.getBoolean("wmsa-debug");
    public static boolean isDebug() {
        return debugMode;
    }
}
