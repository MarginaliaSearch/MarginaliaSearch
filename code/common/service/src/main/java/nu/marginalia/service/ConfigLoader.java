package nu.marginalia.service;

import nu.marginalia.WmsaHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {

    static Path getConfigPath(String configName) {
        return WmsaHome.getHomePath().resolve("conf/properties/" + configName + ".properties");
    }

    static void loadConfig(Path configPath) {
        if (!Files.exists(configPath)) {
            System.err.println("No config file found at " + configPath);
            return;
        }

        System.out.println("Loading config from " + configPath);

        try (var is = Files.newInputStream(configPath)) {
            System.getProperties().load(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
