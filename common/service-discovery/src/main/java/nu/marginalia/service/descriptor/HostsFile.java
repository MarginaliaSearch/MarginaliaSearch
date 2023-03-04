package nu.marginalia.service.descriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Mappings file between ServiceDescriptor.name and host
 *
 * */
public class HostsFile {
    private final Map<String, String> hostsMap = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(HostsFile.class);
    public HostsFile(Path fileName) throws IOException {
        var lines = Files.readAllLines(fileName);
        for (var line : lines) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            String[] parts = line.strip().split(" ");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid hosts file entry " + line);
            String descriptorName = parts[0];
            String hostName = parts[1];

            try {
                hostsMap.put(descriptorName, hostName);
            }
            catch (IllegalArgumentException ex) {
                logger.warn("Hosts file contains entry for unknown service {}", descriptorName);
            }
        }
    }

    public HostsFile() {
    }

    public String getHost(ServiceDescriptor sd) {
        return hostsMap.getOrDefault(sd.name, sd.name);
    }

}
