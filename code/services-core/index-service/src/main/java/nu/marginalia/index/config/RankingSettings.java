package nu.marginalia.index.config;

import lombok.ToString;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ToString
public class RankingSettings {
    public RankingSettingsEntry small;
    public RankingSettingsEntry retro;
    public RankingSettingsEntry standard;
    public RankingSettingsEntry academia;
    public RankingSettingsEntry ranking;

    public static RankingSettings from(Path dir) {
        try {
            return new Yaml().loadAs(Files.readString(dir), RankingSettings.class);
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to load " + dir, ex);
        }
    }
}
