package nu.marginalia.wmsa.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Mappings file between ServiceDescriptor.name and host
 *
 * */
public class HostsFile {
    private final Map<ServiceDescriptor, String> hostsMap = new HashMap<>(ServiceDescriptor.values().length);

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
                hostsMap.put(ServiceDescriptor.byName(descriptorName), hostName);
            }
            catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("ServiceDescriptor " + descriptorName + " invalid");
            }
        }
    }

    public HostsFile() {
        for (var sd : ServiceDescriptor.values()) {
            hostsMap.put(sd, "localhost");
        }
    }

    public String getHost(ServiceDescriptor sd) {
        return hostsMap.get(sd);
    }

}
