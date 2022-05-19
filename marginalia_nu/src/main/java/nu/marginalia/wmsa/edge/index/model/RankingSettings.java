package nu.marginalia.wmsa.edge.index.model;

import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ToString
public class RankingSettings {
    public List<String> small;
    public List<String> retro;
    public List<String> standard;
    public List<String> academia;

    public static RankingSettings from(Path dir) {
        try {
            return new Yaml().loadAs(Files.readString(dir), RankingSettings.class);
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to load " + dir, ex);
        }
    }
}
