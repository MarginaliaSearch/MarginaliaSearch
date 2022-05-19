package nu.marginalia.wmsa.edge.crawling;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.crawling.model.CrawlingSpecification;
import org.apache.logging.log4j.util.Strings;

import java.io.*;
import java.nio.file.Path;
import java.util.function.Consumer;

public class CrawlerSpecificationLoader {
    private final static Gson gson = new GsonBuilder().create();

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
