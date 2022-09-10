package nu.marginalia.wmsa.edge.crawling;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.edge.crawling.model.CrawlingSpecification;
import org.apache.logging.log4j.util.Strings;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.function.Consumer;

public class CrawlerSpecificationLoader {
    private final static Gson gson = GsonFactory.get();

    public static void readInputSpec(Path inputSpec, Consumer<CrawlingSpecification> consumer) {
        try (var inputStream = new BufferedReader(new InputStreamReader(new ZstdInputStream(new FileInputStream(inputSpec.toFile()))))) {

            for (;;) {
                var line = inputStream.readLine();
                if (line == null || !AbortMonitor.getInstance().isAlive())
                    break;

                if (Strings.isNotBlank(line)) {
                    consumer.accept(gson.fromJson(line, CrawlingSpecification.class));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
