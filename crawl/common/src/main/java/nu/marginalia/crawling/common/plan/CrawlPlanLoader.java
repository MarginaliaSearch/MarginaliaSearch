package nu.marginalia.crawling.common.plan;

import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

public class CrawlPlanLoader {
    private final Yaml yaml;

    public CrawlPlanLoader() {
        yaml = new Yaml();
    }

    public EdgeCrawlPlan load(Path yamlFile) throws IOException {
        try (var reader = new FileReader(yamlFile.toFile())) {
            return yaml.loadAs(reader, EdgeCrawlPlan.class);
        }
        catch (IOException ex) {
            throw new IOException("Failed to load crawl plan " + yamlFile, ex);
        }
    }

}
