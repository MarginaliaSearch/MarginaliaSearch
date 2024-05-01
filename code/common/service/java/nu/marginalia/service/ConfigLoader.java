package nu.marginalia.service;

import nu.marginalia.WmsaHome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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
            var toLoad = new Properties();
            toLoad.load(is);
            for (var key : toLoad.keySet()) {
                String k = (String) key;
                System.out.println("Loading property " + k + " = " + toLoad.getProperty(k));
                System.setProperty(k, toLoad.getProperty(k));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
