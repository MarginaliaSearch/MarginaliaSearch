package nu.marginalia.crawling.common.plan;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.JsonStreamParser;
import com.google.gson.stream.JsonReader;
import nu.marginalia.crawling.common.AbortMonitor;
import nu.marginalia.crawling.model.CrawlingSpecification;
import nu.marginalia.model.gson.GsonFactory;
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
            var parser = new JsonStreamParser(inputStream);
            while (parser.hasNext()) {
                consumer.accept(gson.fromJson(parser.next(), CrawlingSpecification.class));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
